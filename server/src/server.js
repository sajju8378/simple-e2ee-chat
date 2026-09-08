import Fastify from 'fastify';
import cors from '@fastify/cors';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

const dataDir = path.resolve(process.env.DATA_DIR || './data');
fs.mkdirSync(dataDir, { recursive: true });
const db = new DatabaseSync(path.join(dataDir, 'chat.sqlite'));

db.exec(`
  PRAGMA journal_mode=WAL;
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    password TEXT NOT NULL,
    public_key TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS sessions (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    created_at TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    sender_id TEXT NOT NULL REFERENCES users(id),
    recipient_id TEXT NOT NULL REFERENCES users(id),
    envelope TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_messages_pair ON messages(sender_id, recipient_id, created_at);
  CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
`);

// One-time migration from the original JSON prototype, if it exists.
const legacyFile = path.join(dataDir, 'store.json');
if (fs.existsSync(legacyFile)) {
  try {
    const legacy = JSON.parse(fs.readFileSync(legacyFile, 'utf8'));
    const insertUser = db.prepare('INSERT OR IGNORE INTO users (id, display_name, password, public_key, created_at) VALUES (?, ?, ?, ?, ?)');
    for (const user of Object.values(legacy.users || {})) {
      insertUser.run(user.id, user.displayName, user.password, user.publicKey, user.createdAt || new Date().toISOString());
    }
    const insertMessage = db.prepare('INSERT OR IGNORE INTO messages (id, sender_id, recipient_id, envelope, created_at) VALUES (?, ?, ?, ?, ?)');
    for (const message of legacy.messages || []) {
      insertMessage.run(message.id, message.from, message.to, JSON.stringify(message.envelope), message.createdAt || new Date().toISOString());
    }
  } catch (e) {
    app.log.warn({ err: e }, 'Legacy JSON migration skipped');
  }
}

function validId(id) { return typeof id === 'string' && /^E2E-[A-Z0-9]{8}$/.test(id); }
function newId() {
  let id;
  do { id = 'E2E-' + crypto.randomBytes(4).toString('hex').toUpperCase(); }
  while (db.prepare('SELECT 1 FROM users WHERE id = ?').get(id));
  return id;
}
function passwordDigest(passwordHash) {
  const salt = crypto.randomBytes(16);
  return `${salt.toString('base64')}.${crypto.scryptSync(passwordHash, salt, 32).toString('base64')}`;
}
function passwordMatches(passwordHash, stored) {
  const [s, h] = String(stored).split('.');
  if (!s || !h) return false;
  const actual = crypto.scryptSync(passwordHash, Buffer.from(s, 'base64'), 32);
  const expected = Buffer.from(h, 'base64');
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}
function issueSession(userId) {
  const token = crypto.randomBytes(32).toString('base64url');
  db.prepare('INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)').run(token, userId, new Date().toISOString());
  return token;
}
function auth(request, reply) {
  const raw = String(request.headers.authorization || '');
  const token = raw.startsWith('Bearer ') ? raw.slice(7) : '';
  if (!token) { reply.code(401).send({ error: 'login required' }); return null; }
  const row = db.prepare('SELECT user_id FROM sessions WHERE token = ?').get(token);
  if (!row) { reply.code(401).send({ error: 'login required' }); return null; }
  return row.user_id;
}

app.get('/', async () => ({ ok: true, service: 'simple-e2ee-chat', status: 'online' }));
app.get('/health', async () => ({
  ok: true,
  service: 'simple-e2ee-chat',
  users: db.prepare('SELECT COUNT(*) AS count FROM users').get().count,
  messages: db.prepare('SELECT COUNT(*) AS count FROM messages').get().count,
  database: 'sqlite'
}));

app.post('/v1/register', async (request, reply) => {
  const { displayName, passwordHash, publicKey } = request.body ?? {};
  if (typeof displayName !== 'string' || !displayName.trim() || displayName.length > 80) return reply.code(400).send({ error: 'display name is required' });
  if (typeof passwordHash !== 'string' || passwordHash.length < 40 || passwordHash.length > 200) return reply.code(400).send({ error: 'invalid password' });
  if (typeof publicKey !== 'string' || publicKey.length < 100 || publicKey.length > 10000) return reply.code(400).send({ error: 'public key is required' });
  const id = newId();
  const createdAt = new Date().toISOString();
  db.prepare('INSERT INTO users (id, display_name, password, public_key, created_at) VALUES (?, ?, ?, ?, ?)').run(id, displayName.trim(), passwordDigest(passwordHash), publicKey, createdAt);
  const token = issueSession(id);
  return reply.code(201).send({ id, token, publicKey, displayName: displayName.trim() });
});

app.post('/v1/login', async (request, reply) => {
  const { id, passwordHash } = request.body ?? {};
  const uid = String(id || '').toUpperCase();
  const user = db.prepare('SELECT id, display_name, password, public_key FROM users WHERE id = ?').get(uid);
  if (!user || typeof passwordHash !== 'string' || !passwordMatches(passwordHash, user.password)) return reply.code(401).send({ error: 'invalid Messenger ID or password' });
  const token = issueSession(uid);
  return { id: uid, token, publicKey: user.public_key, displayName: user.display_name };
});

app.post('/v1/logout', async (request, reply) => {
  const me = auth(request, reply); if (!me) return;
  const raw = String(request.headers.authorization || '');
  db.prepare('DELETE FROM sessions WHERE token = ?').run(raw.slice(7));
  return { ok: true };
});

app.get('/v1/users/:id', async (request, reply) => {
  const me = auth(request, reply); if (!me) return;
  const id = String(request.params.id).toUpperCase();
  if (!validId(id)) return reply.code(400).send({ error: 'invalid id' });
  const user = db.prepare('SELECT id, display_name, public_key FROM users WHERE id = ?').get(id);
  if (!user) return reply.code(404).send({ error: 'user not found' });
  return { id: user.id, displayName: user.display_name, publicKey: user.public_key };
});

app.post('/v1/messages', async (request, reply) => {
  const me = auth(request, reply); if (!me) return;
  const { to, from, envelope } = request.body ?? {};
  const target = String(to || '').toUpperCase();
  if (from !== me || !validId(target) || !envelope || typeof envelope !== 'object') return reply.code(400).send({ error: 'invalid encrypted message' });
  if (!db.prepare('SELECT 1 FROM users WHERE id = ?').get(target)) return reply.code(404).send({ error: 'recipient not found' });
  const id = crypto.randomUUID();
  const createdAt = new Date().toISOString();
  db.prepare('INSERT INTO messages (id, sender_id, recipient_id, envelope, created_at) VALUES (?, ?, ?, ?, ?)').run(id, me, target, JSON.stringify(envelope), createdAt);
  return reply.code(201).send({ id, accepted: true });
});

app.get('/v1/conversations/:peer', async (request, reply) => {
  const me = auth(request, reply); if (!me) return;
  const peer = String(request.params.peer).toUpperCase();
  if (!validId(peer) || !db.prepare('SELECT 1 FROM users WHERE id = ?').get(peer)) return reply.code(404).send({ error: 'user not found' });
  const rows = db.prepare(`
    SELECT id, sender_id AS "from", recipient_id AS "to", envelope, created_at AS "createdAt"
    FROM messages
    WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)
    ORDER BY created_at ASC
  `).all(me, peer, peer, me);
  return { messages: rows.map(row => ({ ...row, envelope: JSON.parse(row.envelope) })) };
});

const port = Number(process.env.PORT || 8080);
await app.listen({ host: '0.0.0.0', port });
