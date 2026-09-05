import Fastify from 'fastify';
import cors from '@fastify/cors';
import crypto from 'node:crypto';

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

// Prototype storage. Replace with a persistent database for deployment.
const users = new Map();
const mailboxes = new Map();

function validId(id) {
  return typeof id === 'string' && /^E2E-[A-Z0-9]{8}$/.test(id);
}

function newId() {
  let id;
  do {
    id = `E2E-${crypto.randomBytes(5).toString('base64url').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8).padEnd(8, '0')}`;
  } while (users.has(id));
  return id;
}

app.get('/health', async () => ({ ok: true, service: 'simple-e2ee-chat' }));

app.post('/v1/users', async (request, reply) => {
  const body = request.body ?? {};
  const publicKey = body.publicKey;
  if (typeof publicKey !== 'string' || publicKey.length < 20 || publicKey.length > 10000) {
    return reply.code(400).send({ error: 'publicKey is required' });
  }

  const id = newId();
  users.set(id, { id, publicKey, createdAt: new Date().toISOString() });
  mailboxes.set(id, []);
  return reply.code(201).send({ id, publicKey });
});

app.get('/v1/users/:id', async (request, reply) => {
  const id = String(request.params.id).toUpperCase();
  if (!validId(id)) return reply.code(400).send({ error: 'invalid id' });
  const user = users.get(id);
  if (!user) return reply.code(404).send({ error: 'user not found' });
  return { id: user.id, publicKey: user.publicKey };
});

app.post('/v1/messages', async (request, reply) => {
  const body = request.body ?? {};
  const { to, from, envelope } = body;
  if (!validId(to) || !validId(from) || !envelope || typeof envelope !== 'object') {
    return reply.code(400).send({ error: 'to, from and encrypted envelope are required' });
  }
  if (!users.has(to) || !users.has(from)) return reply.code(404).send({ error: 'user not found' });

  // IMPORTANT: envelope is opaque to the server. Do not add plaintext here.
  const message = {
    id: crypto.randomUUID(),
    from,
    to,
    envelope,
    createdAt: new Date().toISOString()
  };
  const box = mailboxes.get(to) ?? [];
  box.push(message);
  mailboxes.set(to, box);
  return reply.code(201).send({ id: message.id, accepted: true });
});

app.get('/v1/messages/:id', async (request, reply) => {
  const id = String(request.params.id).toUpperCase();
  if (!validId(id)) return reply.code(400).send({ error: 'invalid id' });
  const box = mailboxes.get(id);
  if (!box) return reply.code(404).send({ error: 'user not found' });
  const messages = [...box];
  mailboxes.set(id, []);
  return { messages };
});

const port = Number(process.env.PORT || 8080);
await app.listen({ host: '0.0.0.0', port });
