package com.simplee2eechat.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.Executors

class CallActivity:Activity(){
 private lateinit var store:SecureStore; private var api:ApiClient?=null; private val exec=Executors.newSingleThreadExecutor(); private val main=Handler(Looper.getMainLooper())
 private lateinit var status:TextView; private var pc:PeerConnection?=null; private var factory:PeerConnectionFactory?=null; private var localVideo:VideoTrack?=null; private var localView:SurfaceViewRenderer?=null; private var remoteView:SurfaceViewRenderer?=null
 private var callId=""; private var caller=false; private var video=false; private var sentCandidates=mutableSetOf<String>(); private var seenCandidates=mutableSetOf<String>()
 private val egl by lazy{EglBase.create()}
 override fun onCreate(b:Bundle?){super.onCreate(b);store=SecureStore(this);api=ApiClient(server(),store.token()?:return);callId=intent.getStringExtra("callId").orEmpty();caller=intent.getBooleanExtra("caller",false);video=intent.getStringExtra("type")=="video";buildUi(); if(!hasPerms()){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA),9)}else start()}
 private fun server()="https://simple-e2ee-chat.onrender.com"
 private fun hasPerms()=ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED && (!video||ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
 override fun onRequestPermissionsResult(r:Int,p:Array<String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==9&&hasPerms())start()else{status.text="Microphone/camera permission is required"}}
 private fun buildUi(){val root=FrameLayout(this);status=TextView(this).apply{text=if(video)"Starting video call…" else "Starting voice call…";textSize=16f;setPadding(20,20,20,20)};root.addView(status);if(video){remoteView=SurfaceViewRenderer(this);remoteView!!.init(egl.eglBaseContext,null);root.addView(remoteView,FrameLayout.LayoutParams(-1,-1));localView=SurfaceViewRenderer(this);localView!!.init(egl.eglBaseContext,null);val lp=FrameLayout.LayoutParams(360,520);lp.leftMargin=20;lp.topMargin=70;root.addView(localView,lp)};val end=Button(this).apply{text="End call";setOnClickListener{endCall()}};val lp=FrameLayout.LayoutParams(-1,60);lp.gravity=android.view.Gravity.BOTTOM;root.addView(end,lp);setContentView(root)}
 private fun start(){PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());factory=PeerConnectionFactory.builder().setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext,true,true)).createPeerConnectionFactory();val ice=PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();pc=factory!!.createPeerConnection(PeerConnection.RTCConfiguration(listOf(ice)),object:PeerConnection.Observer{
  override fun onIceCandidate(c:IceCandidate){postCandidate(c)}
  override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){main.post{status.text=when(s){PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->"Connected • end-to-end media encrypted";PeerConnection.IceConnectionState.FAILED->"Connection failed";else->"Connecting…"}}}
  override fun onTrack(t: RtpTransceiver){if(video)(t.receiver.track() as? VideoTrack)?.addSink(remoteView)}
  override fun onAddStream(s:MediaStream){}
  override fun onDataChannel(d:DataChannel){}
  override fun onRenegotiationNeeded(){}
  override fun onSignalingChange(s:PeerConnection.SignalingState){}
  override fun onIceGatheringChange(s:PeerConnection.IceGatheringState){}
  override fun onIceCandidatesRemoved(c:Array<IceCandidate>){ }
  override fun onIceConnectionReceivingChange(b:Boolean){}
  override fun onRemoveStream(s:MediaStream){}
}) ?: error("Unable to create connection");addLocalMedia();if(caller)makeOffer() else waitForAnswerAndCandidates()}
 private fun addLocalMedia(){val audioSource=factory!!.createAudioSource(MediaConstraints());pc!!.addTrack(factory!!.createAudioTrack("audio",audioSource),listOf("media"));if(video){val capturer=createCapturer();val source=factory!!.createVideoSource(false);capturer.initialize(SurfaceTextureHelper.create("capture",egl.eglBaseContext),this,source.capturerObserver);capturer.startCapture(640,480,24);localVideo=factory!!.createVideoTrack("video",source);localVideo!!.addSink(localView);pc!!.addTrack(localVideo,listOf("media"))}}
 private fun createCapturer():CameraVideoCapturer{val e=Camera2Enumerator(this);return e.deviceNames.firstNotNullOfOrNull{n->if(e.isFrontFacing(n))e.createCapturer(n,null) else null}?:throw IllegalStateException("No front camera")}
 private fun makeOffer(){pc!!.createOffer(object:SimpleSdpObserver(){override fun onCreateSuccess(d:SessionDescription){pc!!.setLocalDescription(SimpleSdpObserver(),d);exec.execute{try{val c=api!!.createCall(intent.getStringExtra("peer")?:error("peer missing"),if(video)"video" else "voice",d.description);callId=c.id;main.post{status.text="Calling…"}}catch(e:Exception){main.post{status.text=e.message?:"Call failed"}}}}},MediaConstraints())}
 private fun waitForAnswerAndCandidates(){exec.execute{try{val info=api!!.call(callId);val offer=info.offer?:error("offer missing");pc!!.setRemoteDescription(SimpleSdpObserver(),SessionDescription(SessionDescription.Type.OFFER,offer));pc!!.createAnswer(object:SimpleSdpObserver(){override fun onCreateSuccess(d:SessionDescription){pc!!.setLocalDescription(SimpleSdpObserver(),d);exec.execute{try{api!!.answerCall(callId,d.description)}catch(_:Exception){}}}},MediaConstraints());main.post{status.text="Answering…"};pollCandidates()}catch(e:Exception){main.post{status.text=e.message?:"Call failed"}}}}
 private fun postCandidate(c:IceCandidate){val j=JSONObject().put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).put("candidate",c.sdp);exec.execute{try{api?.candidate(callId,j.toString())}catch(_:Exception){}}}
 private fun pollCandidates(){main.postDelayed({if(isFinishing)return@postDelayed;exec.execute{try{api?.candidates(callId)?.forEach{addRemoteCandidate(it)}}catch(_:Exception){};main.post{pollCandidates()}}},1500)}
 private fun addRemoteCandidate(s:String){if(!seenCandidates.add(s))return;try{val j=JSONObject(s);pc?.addIceCandidate(IceCandidate(j.optString("sdpMid"),j.getInt("sdpMLineIndex"),j.getString("candidate")))}catch(_:Exception){}}
 private fun endCall(){exec.execute{try{if(callId.isNotBlank())api?.endCall(callId)}catch(_:Exception){};main.post{finish()}}}
 override fun onDestroy(){try{pc?.close();factory?.dispose();egl.release()}catch(_:Exception){};exec.shutdownNow();super.onDestroy()}
 private open class SimpleSdpObserver: SdpObserver{override fun onCreateSuccess(d:SessionDescription){};override fun onSetSuccess(){};override fun onCreateFailure(s:String){};override fun onSetFailure(s:String){}}
}
