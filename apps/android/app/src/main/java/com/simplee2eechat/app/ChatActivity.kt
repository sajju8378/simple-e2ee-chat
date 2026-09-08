package com.simplee2eechat.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.Executors

class ChatActivity : Activity() {
    private enum class Screen { LOGIN, SIGNUP, CHATS, SEARCH, CHAT }
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private lateinit var store:SecureStore
    private lateinit var history:LocalChatStore
    private var api:ApiClient?=null
    private var myId="";private var myName="";private var myUsername=""
    private var peer="";private var peerName="";private var screen=Screen.LOGIN;private var polling=false
    private var pendingSaveImage:ByteArray?=null
    private val bg=0xFFF7F9FC.toInt();private val textColor=0xFF172033.toInt();private val hintColor=0xFF667085.toInt();private val primary=0xFF2563EB.toInt();private val incomingBubble=0xFF263247.toInt()
    private val cameraRequest=4101;private val galleryRequest=4102;private val saveImageRequest=4103;private val saveChatRequest=4104
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun tv(s:String,size:Float=16f,bold:Boolean=false,color:Int=textColor)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun ed(h:String)=EditText(this).apply{hint=h;setTextColor(textColor);setHintTextColor(hintColor);isSingleLine=true;textSize=16f;setPadding(dp(16),0,dp(16),0);background=round(Color.WHITE,14,0xFFD9E0EA.toInt())}
    private fun btn(s:String,primaryBtn:Boolean=false)=Button(this).apply{text=s;isAllCaps=false;setTextColor(if(primaryBtn)Color.WHITE else textColor);background=round(if(primaryBtn)primary else Color.WHITE,14,if(primaryBtn)primary else 0xFFD9E0EA.toInt());stateListAnimator=null;minHeight=dp(48)}
    private fun round(fill:Int,r:Int,stroke:Int=0)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();if(stroke!=0)setStroke(dp(1),stroke)}
    private fun root()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(dp(20),dp(18),dp(20),dp(16))}
    private fun server()="https://simple-e2ee-chat.onrender.com"

    override fun onCreate(b:Bundle?){super.onCreate(b);store=SecureStore(this);history=LocalChatStore(this);val id=store.userId();val pw=store.password();if(!id.isNullOrBlank()&&!pw.isNullOrBlank())login(id,pw,true)else showLogin()}
    override fun onDestroy(){polling=false;main.removeCallbacksAndMessages(null);executor.shutdownNow();history.closeStore();super.onDestroy()}
    @Suppress("DEPRECATION") override fun onBackPressed(){when(screen){Screen.CHAT->{polling=false;showChats()};Screen.SEARCH->showChats();Screen.SIGNUP->showLogin();Screen.LOGIN->super.onBackPressed();Screen.CHATS->super.onBackPressed()}}

    private fun login(id:String,pw:String,automatic:Boolean=false){
        screen=Screen.LOGIN
        executor.execute{try{
            val r=ApiClient.login(server(),id.trim(),pw)
            var privateKey=store.savedPrivateKey(r.id)
            var rebuiltLegacyKey=false
            if(privateKey.isNullOrBlank() && r.keyBackup.isNotBlank()) privateKey=Crypto.decryptPrivateKeyBackup(r.keyBackup,pw)
            val c=ApiClient(server(),r.token)
            if(privateKey.isNullOrBlank()){
                // Accounts created by the original APK never had a recoverable private-key backup.
                // The password still authenticates the account, so create a fresh device key and
                // replace the server public key. This keeps the account/username usable instead of
                // trapping the user on the login screen. Messages encrypted to the retired key
                // remain on the server but cannot be decrypted without that old private key.
                val k=Crypto.generateKeyPair()
                c.rotateDeviceKey(k.publicKey,Crypto.encryptPrivateKeyBackup(k.privateKey,pw))
                privateKey=k.privateKey
                rebuiltLegacyKey=true
            } else if(r.keyBackup.isBlank()) try{c.uploadKeyBackup(Crypto.encryptPrivateKeyBackup(privateKey,pw))}catch(_:Exception){}
            store.activateSavedAccount(r.id,r.token,r.publicKey,pw.ifBlank{r.displayName},pw,privateKey)
            // Store the actual current public key when a legacy key was rebuilt.
            if(rebuiltLegacyKey){
                val current=c.getUser(r.id)
                store.activateSavedAccount(r.id,r.token,current.publicKey,r.displayName,pw,privateKey)
            } else store.activateSavedAccount(r.id,r.token,r.publicKey,r.displayName,pw,privateKey)
            myId=r.id;myName=r.displayName;myUsername=r.username;api=c
            main.post{showChats();if(rebuiltLegacyKey)Toast.makeText(this,"Account recovered with a new device key. Old messages encrypted to the previous key cannot be opened.",Toast.LENGTH_LONG).show()}
        }catch(e:Exception){main.post{showLogin(if(automatic)"Session needs attention: ${e.message?:"please sign in again"}" else e.message?:"Login failed")}}}
    }

    private fun showLogin(message:String=""){
        polling=false;screen=Screen.LOGIN;val r=root();r.gravity=Gravity.CENTER_HORIZONTAL
        r.addView(tv("Simple E2EE Chat",30f,true));r.addView(tv("Private messaging without a phone number",16f).apply{setPadding(0,dp(8),0,dp(28))})
        val id=ed("Your ID or username");val pw=ed("Password");pw.inputType=129
        r.addView(id,LinearLayout.LayoutParams(-1,dp(56)).apply{bottomMargin=dp(12)});r.addView(pw,LinearLayout.LayoutParams(-1,dp(56)).apply{bottomMargin=dp(12)})
        val status=tv(message,14f,false,if(message.isBlank())hintColor else 0xFFB42318.toInt());r.addView(status,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
        val b=btn("Log in",true);r.addView(b,LinearLayout.LayoutParams(-1,dp(52)));val c=btn("Create a new account");r.addView(c,LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(10)})
        r.addView(tv("Your private key is encrypted for recovery and never uploaded in plaintext.",12f,false,hintColor).apply{setPadding(0,dp(12),0,0)});setContentView(r)
        b.setOnClickListener{val u=id.text.toString().trim();val p=pw.text.toString();if(u.isBlank()||p.length<8){status.text="Enter your ID/username and 8+ character password";return@setOnClickListener};b.isEnabled=false;status.text="Signing in…";login(u,p)}
        c.setOnClickListener{showSignup()}
    }

    private fun showSignup(){
        polling=false;screen=Screen.SIGNUP;val r=root();r.addView(tv("Create your account",28f,true));r.addView(tv("Choose an ID that friends can search for.",15f).apply{setPadding(0,dp(8),0,dp(22))})
        val username=ed("Your ID  •  e.g. shiva20");val name=ed("Your name");val pw=ed("Password  •  8+ characters");pw.inputType=129
        r.addView(username,LinearLayout.LayoutParams(-1,dp(56)).apply{bottomMargin=dp(10)});r.addView(name,LinearLayout.LayoutParams(-1,dp(56)).apply{bottomMargin=dp(10)});r.addView(pw,LinearLayout.LayoutParams(-1,dp(56)).apply{bottomMargin=dp(12)})
        val status=tv("IDs: 3–20 characters, starting with a letter.",13f);r.addView(status);val create=btn("Create account",true);r.addView(create,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(10)});val back=btn("Back to login");r.addView(back,LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(8)});setContentView(r)
        create.setOnClickListener{val u=username.text.toString().trim();val n=name.text.toString().trim();val p=pw.text.toString();if(!u.matches(Regex("[A-Za-z][A-Za-z0-9_]{2,19}"))||n.isBlank()||p.length<8){status.text="Please enter a valid ID, name and 8+ character password";return@setOnClickListener};create.isEnabled=false;status.text="Creating secure identity…";executor.execute{try{val k=Crypto.generateKeyPair();val backup=Crypto.encryptPrivateKeyBackup(k.privateKey,p);val a=ApiClient.register(server(),u,n,p,k.publicKey,backup);store.saveAccount(a.id,a.token,k.privateKey,a.publicKey,n,p);myId=a.id;myUsername=a.username;myName=a.displayName;api=ApiClient(server(),a.token);main.post{showChats()}}catch(e:Exception){main.post{create.isEnabled=true;status.text=e.message?:"Could not create account"}}}}
        back.setOnClickListener{showLogin()}
    }

    private fun showChats(){
        polling=false;screen=Screen.CHATS;val r=root();r.setPadding(0,0,0,0);val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(18),dp(12),dp(14));setBackgroundColor(Color.WHITE)};val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};title.addView(tv("Chats",28f,true));title.addView(tv(if(myUsername.isBlank())myName else "@$myUsername",13f));head.addView(title,LinearLayout.LayoutParams(0,-2,1f));val me=btn("My ID");head.addView(me,LinearLayout.LayoutParams(dp(76),dp(44)));r.addView(head)
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))};r.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f));loadChatList(list)
        val bottom=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(12),dp(8),dp(12),dp(12));setBackgroundColor(Color.WHITE)};val n=btn("＋ New chat",true);val out=btn("Log out");bottom.addView(n,LinearLayout.LayoutParams(0,dp(52),1f).apply{rightMargin=dp(8)});bottom.addView(out,LinearLayout.LayoutParams(dp(92),dp(52)));r.addView(bottom);setContentView(r)
        n.setOnClickListener{showSearch()};me.setOnClickListener{Toast.makeText(this,"Your ID: @${myUsername.ifBlank{myId}}",Toast.LENGTH_LONG).show()};out.setOnClickListener{logout()}
    }
    private fun loadChatList(list:LinearLayout){list.removeAllViews();executor.execute{val chats=history.chats();main.post{if(chats.isEmpty()){list.addView(tv("No chats yet",20f,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(60),0,dp(10))});list.addView(tv("Tap New chat and search for a friend's ID.",14f).apply{gravity=Gravity.CENTER});return@post};chats.forEach{c->val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));background=round(Color.WHITE,14,0xFFE0E5EC.toInt())};row.addView(tv(c.peerName,17f,true));row.addView(tv(if(c.lastText.startsWith("{")&&c.lastText.contains("\"type\":\"image\""))"📷 Photo" else c.lastText.take(70),14f));row.setOnClickListener{peer=c.peerId;peerName=c.peerName;showChat()};list.addView(row,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(8)})}}}}

    private fun showSearch(){polling=false;screen=Screen.SEARCH;val r=root();r.setPadding(dp(16),dp(12),dp(16),dp(16));val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};val back=btn("‹");top.addView(back,LinearLayout.LayoutParams(dp(52),dp(48)));top.addView(tv("New chat",23f,true).apply{setPadding(dp(10),0,0,0)});r.addView(top);r.addView(tv("Search by ID or name",15f).apply{setPadding(dp(4),dp(12),0,dp(8))});val q=ed("Start typing a name or ID…");r.addView(q,LinearLayout.LayoutParams(-1,dp(56)));val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};r.addView(results,LinearLayout.LayoutParams(-1,0,1f));val status=tv("Type one or more letters to see matching users.",13f);r.addView(status);setContentView(r);back.setOnClickListener{showChats()};q.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchUsers(s?.toString().orEmpty(),results,status)};override fun afterTextChanged(e:Editable?) {}})}
    private fun searchUsers(q:String,box:LinearLayout,status:TextView){box.removeAllViews();if(q.isBlank()){status.text="Type one or more letters to see matching users.";return};status.text="Searching…";executor.execute{try{val users=api?.searchUsers(q).orEmpty();main.post{box.removeAllViews();if(users.isEmpty()){status.text="No match found";return@post};status.text="Select a user";users.forEach{u->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(9),dp(10),dp(9));background=round(Color.WHITE,14,0xFFE0E5EC.toInt())};val a=TextView(this).apply{text=u.displayName.take(1).uppercase();textSize=19f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;background=round(primary,50)};row.addView(a,LinearLayout.LayoutParams(dp(46),dp(46)).apply{rightMargin=dp(12)});val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};info.addView(tv(u.displayName,16f,true));info.addView(tv("@${u.username}",13f));row.addView(info,LinearLayout.LayoutParams(0,-2,1f));row.setOnClickListener{peer=u.id;peerName=u.displayName;showChat()};box.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(7)})}}}catch(e:Exception){main.post{status.text=e.message?:"Search failed"}}}}

    private fun showChat(){polling=true;screen=Screen.CHAT;val r=root();r.setPadding(0,0,0,0);val h=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8));setBackgroundColor(Color.WHITE)};val back=btn("‹");h.addView(back,LinearLayout.LayoutParams(dp(50),dp(48)));h.addView(tv(peerName,18f,true).apply{setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(0,-2,1f));val menu=btn("⋮");h.addView(menu,LinearLayout.LayoutParams(dp(50),dp(48)));r.addView(h);val msgs=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(8))};val scroll=ScrollView(this).apply{addView(msgs)};r.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));val compose=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(10),dp(8),dp(10),dp(12));setBackgroundColor(Color.WHITE)};val attach=btn("📎");val camera=btn("📷");val input=ed("Message");val send=btn("Send",true);compose.addView(attach,LinearLayout.LayoutParams(dp(48),dp(52)).apply{rightMargin=dp(5)});compose.addView(camera,LinearLayout.LayoutParams(dp(48),dp(52)).apply{rightMargin=dp(5)});compose.addView(input,LinearLayout.LayoutParams(0,dp(52),1f).apply{rightMargin=dp(8)});compose.addView(send,LinearLayout.LayoutParams(dp(82),dp(52)));r.addView(compose);setContentView(r)
        back.setOnClickListener{polling=false;showChats()};menu.setOnClickListener{showChatMenu(menu,msgs)}
        send.setOnClickListener{val body=input.text.toString().trim();if(body.isBlank())return@setOnClickListener;send.isEnabled=false;executor.execute{try{val c=api?:throw IllegalStateException("Please log in again");val u=c.getUser(peer);val id=c.sendMessage(peer,myId,Crypto.encrypt(body,u.publicKey));history.upsert(id,peer,peerName,myId,System.currentTimeMillis(),body);main.post{input.setText("");send.isEnabled=true;renderLocal(msgs,scroll)}}catch(e:Exception){main.post{send.isEnabled=true;Toast.makeText(this,e.message?:"Send failed",Toast.LENGTH_SHORT).show()}}}};attach.setOnClickListener{openGallery()};camera.setOnClickListener{openCamera()};renderLocal(msgs,scroll);syncMessages(msgs,scroll)
    }

    private fun showChatMenu(anchor:View,msgs:LinearLayout){val p=PopupMenu(this,anchor);p.menu.add("Save chat");p.menu.add("Delete chat from this phone");p.menu.add("Log out");p.setOnMenuItemClickListener{item->when(item.title.toString()){"Save chat"->saveChat();"Delete chat from this phone"->AlertDialog.Builder(this).setTitle("Delete local chat?").setMessage("This removes the encrypted chat history stored on this phone. Messages on the server are not deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->executor.execute{history.deletePeer(peer);main.post{msgs.removeAllViews();showChats()}}}.show();"Log out"->logout()};true};p.show()}
    private fun logout(){val old=api;api=null;polling=false;store.logout();myId="";myName="";myUsername="";showLogin("Logged out. Enter an account ID and password.");executor.execute{try{old?.logout()}catch(_:Exception){}}}
    private fun saveChat(){executor.execute{val has=history.items(peer).isNotEmpty();main.post{if(!has){Toast.makeText(this,"No local messages to save",Toast.LENGTH_SHORT).show();return@post};startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="text/plain";putExtra(Intent.EXTRA_TITLE,"${peerName.replace(Regex("[^A-Za-z0-9_-]"),"_")}_chat.txt")},saveChatRequest)}}}
    private fun exportChat(uri:Uri){executor.execute{try{val sb=StringBuilder();history.items(peer).forEach{val who=if(it.senderId==myId)"Me" else peerName;sb.append(who).append(": ").append(if(it.payload.startsWith("{")&&it.payload.contains("\"type\":\"image\""))"[Photo]" else it.payload).append('\n')};contentResolver.openOutputStream(uri).use{it?.write(sb.toString().toByteArray(Charsets.UTF_8))};main.post{Toast.makeText(this,"Chat saved",Toast.LENGTH_SHORT).show()}}catch(e:Exception){main.post{Toast.makeText(this,e.message?:"Could not save chat",Toast.LENGTH_SHORT).show()}}}}
    private fun openGallery(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},galleryRequest)}
    private fun openCamera(){try{startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),cameraRequest)}catch(_:Exception){Toast.makeText(this,"Camera is not available",Toast.LENGTH_SHORT).show()}}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK)return;try{when(requestCode){galleryRequest->{val b=data?.data?.let{decodeUri(it)};if(b==null)Toast.makeText(this,"Could not read the photo",Toast.LENGTH_SHORT).show()else sendImage(b,"photo.jpg")};cameraRequest->{val b=data?.extras?.get("data") as? Bitmap;if(b==null)Toast.makeText(this,"Could not capture the photo",Toast.LENGTH_SHORT).show()else sendImage(bitmapBytes(b),"camera.jpg")};saveImageRequest->{data?.data?.let{saveImage(it)}};saveChatRequest->{data?.data?.let{exportChat(it)}}}}catch(e:Exception){Toast.makeText(this,e.message?:"Attachment failed",Toast.LENGTH_SHORT).show()}}
    private fun decodeUri(uri:Uri):ByteArray?=contentResolver.openInputStream(uri)?.use{compressImage(it.readBytes())}
    private fun bitmapBytes(b:Bitmap):ByteArray{val o=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,88,o);return compressImage(o.toByteArray())}
    private fun compressImage(bytes:ByteArray):ByteArray{val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return bytes;var w=b.width;var h=b.height;val max=1280f;if(w>max||h>max){val s=max/maxOf(w,h);w=(w*s).toInt();h=(h*s).toInt()};val scaled=if(w!=b.width||h!=b.height)Bitmap.createScaledBitmap(b,w,h,true)else b;val o=ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,82,o);if(scaled!==b)scaled.recycle();b.recycle();return o.toByteArray()}
    private fun sendImage(bytes:ByteArray,name:String){if(bytes.size>520_000){Toast.makeText(this,"Photo is too large after compression",Toast.LENGTH_SHORT).show();return};executor.execute{try{val c=api?:throw IllegalStateException("Please log in again");val u=c.getUser(peer);val payload=JSONObject().put("type","image").put("name",name).put("mime","image/jpeg").put("data",Base64.encodeToString(bytes,Base64.NO_WRAP)).toString();val id=c.sendMessage(peer,myId,Crypto.encrypt(payload,u.publicKey));history.upsert(id,peer,peerName,myId,System.currentTimeMillis(),payload);main.post{Toast.makeText(this,"Photo sent",Toast.LENGTH_SHORT).show();showChat()}}catch(e:Exception){main.post{Toast.makeText(this,e.message?:"Photo send failed",Toast.LENGTH_SHORT).show()}}}}
    private fun renderLocal(msgs:LinearLayout,scroll:ScrollView){executor.execute{val items=history.items(peer);main.post{if(screen!=Screen.CHAT)return@post;msgs.removeAllViews();items.forEach{renderItem(msgs,it)};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}}
    private fun renderItem(box:LinearLayout,item:LocalChatStore.Item){val mine=item.senderId==myId;val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=if(mine)Gravity.END else Gravity.START;setPadding(dp(4),dp(3),dp(4),dp(3))};val payload=item.payload;if(payload.startsWith("{")&&payload.contains("\"type\":\"image\"")){try{val j=JSONObject(payload);val bytes=Base64.decode(j.getString("data"),Base64.DEFAULT);val img=ImageView(this).apply{setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.size));adjustViewBounds=true;maxWidth=dp(250);setPadding(dp(3),dp(3),dp(3),dp(3));background=round(if(mine)Color.WHITE else incomingBubble,18);setOnLongClickListener{pendingSaveImage=bytes;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="image/jpeg";putExtra(Intent.EXTRA_TITLE,j.optString("name","photo.jpg"))},saveImageRequest);true}};row.addView(img,LinearLayout.LayoutParams(dp(250),dp(220)))}catch(_:Exception){row.addView(tv("[Photo unavailable]",14f,false,if(mine)textColor else Color.WHITE))}}else{val bubble=tv(payload,16f,false,if(mine)textColor else Color.WHITE).apply{setPadding(dp(14),dp(9),dp(14),dp(9));setMaxWidth(dp(290));isSingleLine=false;ellipsize=null;setHorizontallyScrolling(false);background=round(if(mine)Color.WHITE else incomingBubble,18)};row.addView(bubble,LinearLayout.LayoutParams(-2,-2))};box.addView(row)}
    private fun saveImage(uri:Uri){val bytes=pendingSaveImage?:return;executor.execute{try{contentResolver.openOutputStream(uri).use{it?.write(bytes)};main.post{Toast.makeText(this,"Photo saved",Toast.LENGTH_SHORT).show()}}catch(e:Exception){main.post{Toast.makeText(this,e.message?:"Could not save photo",Toast.LENGTH_SHORT).show()}}}}
    private fun syncMessages(msgs:LinearLayout,scroll:ScrollView){executor.execute{try{val c=api?:return@execute;val key=store.privateKeyBlob()?:return@execute;c.conversation(peer).forEach{m->if(m.from!=myId)try{val plain=Crypto.decrypt(m.envelope,key);val at=try{Instant.parse(m.createdAt).toEpochMilli()}catch(_:Exception){System.currentTimeMillis()};history.upsert(m.id,peer,peerName,m.from,at,plain)}catch(_:Exception){}};main.post{if(screen==Screen.CHAT){renderLocal(msgs,scroll);main.postDelayed({if(polling)syncMessages(msgs,scroll)},3000)}}}catch(_:Exception){main.post{if(polling)main.postDelayed({if(polling)syncMessages(msgs,scroll)},3000)}}}}
}