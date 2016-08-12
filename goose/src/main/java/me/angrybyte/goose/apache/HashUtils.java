/**
 * Licensed to Gravity.com under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Gravity.com licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package me.angrybyte.goose.apache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Doing hashing for stuff.
 */
public class HashUtils {

    /**
     * Return a string of 40 lower case hex characters.
     *
     * @return a string of 40 hex characters
     */
    @SuppressWarnings("unused")
    public static String sha1(String input) {
        String hexHash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(input.getBytes());
            byte[] output = md.digest();
            hexHash = bytesToLowerCaseHex(output);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return hexHash;
    }

    /**
     * Return a string of 32 lower case hex characters.
     *
     * @return a string of 32 hex characters
     */
    public static String md5(String input) {
        String hexHash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] output = md.digest();
            hexHash = bytesToLowerCaseHex(output);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return hexHash;
    }

    @SuppressWarnings("unused")
    private static String bytesToUpperCaseHex(byte[] b) {
        char hexDigit[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        StringBuilder buf = new StringBuilder();
        // noinspection ForLoopReplaceableByForEach
        for (int j = 0; j < b.length; j++) {
            buf.append(hexDigit[(b[j] >> 4) & 0x0f]);
            buf.append(hexDigit[b[j] & 0x0f]);
        }
        return buf.toString();
    }

    private static String bytesToLowerCaseHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < data.length; i++) {
            int halfByte = (data[i] >>> 4) & 0x0F;
            int twoHalves = 0;
            do {
                if ((0 <= halfByte) && (halfByte <= 9)) {
                    buf.append((char) ('0' + halfByte));
                } else {
                    buf.append((char) ('a' + (halfByte - 10)));
                }
                halfByte = data[i] & 0x0F;
            } while (twoHalves++ < 1);
        }
        return buf.toString();
    }

}
