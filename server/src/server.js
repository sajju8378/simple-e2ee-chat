import Fastify from 'fastify';
import cors from '@fastify/cors';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const app=Fastify({logger:true}); await app.register(cors,{origin:true});
const dataDir=path.resolve(process.env.DATA_DIR||'./data'); const dataFile=path.join(dataDir,'store.json'); fs.mkdirSync(dataDir,{recursive:true});
let db={users:{},messages:[]}; try{if(fs.existsSync(dataFile))db=JSON.parse(fs.readFileSync(dataFile,'utf8'));}catch{}
const sessions=new Map(); function save(){fs.writeFileSync(dataFile,JSON.stringify(db));}
function validId(id){return typeof id==='string'&&/^E2E-[A-Z0-9]{8}$/.test(id)}
function newId(){let id;do{id=`E2E-${crypto.randomBytes(8).toString('base64url').toUpperCase().replace(/[^A-Z0-9]/g,'').slice(0,8).padEnd(8,'0')}`}while(db.users[id]);return id}
function passwordDigest(passwordHash){const salt=crypto.randomBytes(16);return `${salt.toString('base64')}.${crypto.scryptSync(passwordHash,salt,32).toString('base64')}`}
function passwordMatches(passwordHash,stored){const [s,h]=String(stored).split('.');if(!s||!h)return false;const actual=crypto.scryptSync(passwordHash,Buffer.from(s,'base64'),32),expected=Buffer.from(h,'base64');return expected.length===actual.length&&crypto.timingSafeEqual(expected,actual)}
function auth(request,reply){const raw=String(request.headers.authorization||'');const token=raw.startsWith('Bearer ')?raw.slice(7):'';const id=sessions.get(token);if(!id){reply.code(401).send({error:'login required'});return null}return id}
app.get('/health',async()=>({ok:true,service:'simple-e2ee-chat',users:Object.keys(db.users).length}));
app.post('/v1/register',async(request,reply)=>{const{displayName,passwordHash,publicKey}=request.body??{};if(typeof displayName!=='string'||!displayName.trim()||displayName.length>80)return reply.code(400).send({error:'display name is required'});if(typeof passwordHash!=='string'||passwordHash.length<40||passwordHash.length>200)return reply.code(400).send({error:'invalid password'});if(typeof publicKey!=='string'||publicKey.length<100||publicKey.length>10000)return reply.code(400).send({error:'public key is required'});const id=newId();db.users[id]={id,displayName:displayName.trim(),password:passwordDigest(passwordHash),publicKey,createdAt:new Date().toISOString()};save();const token=crypto.randomBytes(32).toString('base64url');sessions.set(token,id);return reply.code(201).send({id,token,publicKey})});
app.post('/v1/login',async(request,reply)=>{const{id,passwordHash}=request.body??{};const uid=String(id||'').toUpperCase(),user=db.users[uid];if(!user||typeof passwordHash!=='string'||!passwordMatches(passwordHash,user.password))return reply.code(401).send({error:'invalid Messenger ID or password'});const token=crypto.randomBytes(32).toString('base64url');sessions.set(token,uid);return{id:uid,token,publicKey:user.publicKey}});
app.get('/v1/users/:id',async(request,reply)=>{const me=auth(request,reply);if(!me)return;const id=String(request.params.id).toUpperCase();if(!validId(id))return reply.code(400).send({error:'invalid id'});const user=db.users[id];if(!user)return reply.code(404).send({error:'user not found'});return{id:user.id,displayName:user.displayName,publicKey:user.publicKey}});
app.post('/v1/messages',async(request,reply)=>{const me=auth(request,reply);if(!me)return;const{to,from,envelope}=request.body??{},target=String(to||'').toUpperCase();if(from!==me||!validId(target)||!envelope||typeof envelope!=='object')return reply.code(400).send({error:'invalid encrypted message'});if(!db.users[target])return reply.code(404).send({error:'recipient not found'});const message={id:crypto.randomUUID(),from:me,to:target,envelope,createdAt:new Date().toISOString()};db.messages.push(message);save();return reply.code(201).send({id:message.id,accepted:true})});
app.get('/v1/conversations/:peer',async(request,reply)=>{const me=auth(request,reply);if(!me)return;const peer=String(request.params.peer).toUpperCase();if(!validId(peer)||!db.users[peer])return reply.code(404).send({error:'user not found'});return{messages:db.messages.filter(m=>(m.from===me&&m.to===peer)||(m.from===peer&&m.to===me))}});
const port=Number(process.env.PORT||8080);await app.listen({host:'0.0.0.0',port});
