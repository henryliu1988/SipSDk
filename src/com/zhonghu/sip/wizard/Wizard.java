package com.zhonghu.sip.wizard;

public class Wizard extends SimpleImplementation {

	public Wizard(String username, String password) {
		userName = username;
		passWord = password;
	}

	public Wizard(String site, String username, String password) {
		domain = site;
		userName = username;
		passWord = password;
	}

	@Override
	protected String getDomain() {
		// TODO Auto-generated method stub
		// return "112.253.6.70:2060";
		// return "119.81.176.83:5060";
		// return "119.191.59.51:2060";
		// return "http://192.168.2.235:5060";
		return domain;
	}

	@Override
	protected String getDefaultName() {
		// TODO Auto-generated method stub
		return "zhonghu400";
	}

	@Override
	public boolean canSave() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected String getUserName() {
		// TODO Auto-generated method stub
		return userName;
	}

	@Override
	protected String getPassword() {
		// TODO Auto-generated method stub
		return passWord;
	}

	public void setUserName(String username) {
		this.userName = username;
	}

	public void setPassWord(String password) {
		this.passWord = password;
	}

}
