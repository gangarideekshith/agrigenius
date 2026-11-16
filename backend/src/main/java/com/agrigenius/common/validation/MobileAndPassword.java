package com.agrigenius.common.validation;

/** Implemented by request records that carry both a mobile number and a password. */
public interface MobileAndPassword {

    String mobileNumber();

    String password();
}
