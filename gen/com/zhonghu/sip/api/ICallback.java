/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: E:\\400\\企业通讯项目\\Android\\Code\\versionSDK\\SipSDk\\src\\com\\zhonghu\\sip\\api\\ICallback.aidl
 */
package com.zhonghu.sip.api;
public interface ICallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.zhonghu.sip.api.ICallback
{
private static final java.lang.String DESCRIPTOR = "com.zhonghu.sip.api.ICallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.zhonghu.sip.api.ICallback interface,
 * generating a proxy if needed.
 */
public static com.zhonghu.sip.api.ICallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.zhonghu.sip.api.ICallback))) {
return ((com.zhonghu.sip.api.ICallback)iin);
}
return new com.zhonghu.sip.api.ICallback.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_onSipCallChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.onSipCallChanged(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onSipMediaChanged:
{
data.enforceInterface(DESCRIPTOR);
this.onSipMediaChanged();
reply.writeNoException();
return true;
}
case TRANSACTION_onCallLaunched:
{
data.enforceInterface(DESCRIPTOR);
this.onCallLaunched();
reply.writeNoException();
return true;
}
case TRANSACTION_onInCommingCall:
{
data.enforceInterface(DESCRIPTOR);
this.onInCommingCall();
reply.writeNoException();
return true;
}
case TRANSACTION_onCallInfoListChanged:
{
data.enforceInterface(DESCRIPTOR);
this.onCallInfoListChanged();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.zhonghu.sip.api.ICallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void onSipCallChanged(int stateCode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(stateCode);
mRemote.transact(Stub.TRANSACTION_onSipCallChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onSipMediaChanged() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onSipMediaChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onCallLaunched() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onCallLaunched, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onInCommingCall() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onInCommingCall, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onCallInfoListChanged() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onCallInfoListChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_onSipCallChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onSipMediaChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_onCallLaunched = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_onInCommingCall = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_onCallInfoListChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
}
public void onSipCallChanged(int stateCode) throws android.os.RemoteException;
public void onSipMediaChanged() throws android.os.RemoteException;
public void onCallLaunched() throws android.os.RemoteException;
public void onInCommingCall() throws android.os.RemoteException;
public void onCallInfoListChanged() throws android.os.RemoteException;
}
