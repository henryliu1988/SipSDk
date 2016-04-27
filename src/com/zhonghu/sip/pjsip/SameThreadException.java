package com.zhonghu.sip.pjsip;


public class SameThreadException extends Exception {
	private static final long serialVersionUID = -905639124232613768L;

	public SameThreadException() {
		super("Should be launched from a single worker thread");
	}
}
