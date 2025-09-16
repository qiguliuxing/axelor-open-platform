/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class TestEncryptors {

  private static final String SECRET_KEY = "MySuperSecretKey";

  private byte[] generateRandomBytes(int size) {
    byte[] value = new byte[size];
    new SecureRandom().nextBytes(value);
    return value;
  }

  private void testBytesEncryptor(BytesEncryptor encryptor) {

    final byte[] value = generateRandomBytes(18);

    byte[] encrypted = encryptor.encrypt(value);
    byte[] decrypted = encryptor.decrypt(encrypted);

    assertNotNull(encrypted);
    assertFalse(Arrays.equals(encrypted, value));
    assertTrue(Arrays.equals(decrypted, value));

    // make sure to have special prefix
    byte[] prefix = new byte[BytesEncryptor.PREFIX_BYTES.length];
    System.arraycopy(encrypted, 0, prefix, 0, prefix.length);

    assertTrue(Arrays.equals(BytesEncryptor.PREFIX_BYTES, prefix));
  }

  private void testStringEncryptor(StringEncryptor encryptor) {

    final String value = "Hello World!!!";

    String encrypted = encryptor.encrypt(value);
    String decrypted = encryptor.decrypt(encrypted);

    assertNotNull(encrypted);
    assertNotEquals(encrypted, value);
    assertEquals(decrypted, value);
  }

  @Test
  public void testBytesCBC() {
    testBytesEncryptor(BytesEncryptor.cbc(SECRET_KEY));
  }

  @Test
  public void testBytesGCM() {
    testBytesEncryptor(BytesEncryptor.gcm(SECRET_KEY));
  }

  @Test
  public void testStringCBC() {
    testStringEncryptor(StringEncryptor.cbc(SECRET_KEY));
  }

  @Test
  public void testStringGCM() {
    testStringEncryptor(StringEncryptor.gcm(SECRET_KEY));
  }
}
