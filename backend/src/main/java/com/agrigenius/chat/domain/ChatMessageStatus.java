package com.agrigenius.chat.domain;

/** OK / NO_SOURCES / REFUSED are model outcomes; FAILED marks a provider failure. USER messages are always OK. */
public enum ChatMessageStatus { OK, FAILED, REFUSED, NO_SOURCES }
