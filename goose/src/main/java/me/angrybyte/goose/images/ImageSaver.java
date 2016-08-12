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

package me.angrybyte.goose.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.protocol.ClientContext;
import cz.msebera.android.httpclient.protocol.BasicHttpContext;
import cz.msebera.android.httpclient.protocol.HttpContext;
import me.angrybyte.goose.network.HtmlFetcher;

/**
 * This class will be responsible for storing images to disk
 */
public class ImageSaver {

    /**
     * Stores an image to disk and returns the name of the file
     */
    public static String storeTempImage(HttpClient httpClient, String linkHash, String imageSrc, String cacheDirectory, int minPicSize) throws Exception {
        ;// Log.d(ImageSaver.class.getSimpleName(), "Starting to download image: " + imageSrc);
        imageSrc = imageSrc.replace(" ", "%20");

        HttpGet getRequest = null;
        try {
            // download it
            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(ClientContext.COOKIE_STORE, HtmlFetcher.emptyCookieStore);
            getRequest = new HttpGet(imageSrc);
            HttpResponse response = httpClient.execute(getRequest, localContext);

            String respStatus = response.getStatusLine().toString();
            if (!respStatus.contains("200")) {
                return null;
            }

            // get preliminary extension (if any)
            HttpEntity entity = response.getEntity();
            String webType = null;
            try {
                webType = ImageUtils.getFileExtensionSimple(entity.getContentType().getValue());
            } catch (Exception e) {
                ;// Log.e(ImageSaver.class.getSimpleName(), e.getMessage());
            }
            if (webType == null) {
                webType = "";
            }

            // generate random name
            int randInt = new Random().nextInt();
            String fileName = linkHash + "_" + randInt + webType;
            String fileNameRaw = linkHash + "_" + randInt;
            String filePath = cacheDirectory + File.separator + fileName;
            String filePathRaw = cacheDirectory + File.separator + fileNameRaw;

            if (entity != null) {
                // save it to temporary cache
                Bitmap webBitmap = BitmapFactory.decodeStream(entity.getContent());
                FileOutputStream fileStream = new FileOutputStream(filePath);
                ByteArrayOutputStream webBitmapStream = new ByteArrayOutputStream();

                webBitmap.compress(Bitmap.CompressFormat.JPEG, 100, webBitmapStream);
                byte[] byteArray = webBitmapStream.toByteArray();
                fileStream.write(byteArray);
                fileStream.flush();
                close(fileStream);
                close(webBitmapStream);

                // get mime type and store the image extension based on that
                String mimeExtension = ImageUtils.getFileExtension(filePath);
                if (TextUtils.isEmpty(mimeExtension)) {
                    ;// Log.e(ImageSaver.class.getSimpleName(), "Cannot read file extension from " + filePath);
                    return null;
                }

                File f = new File(filePath);
                if (f.length() < minPicSize) {
                    return null;
                }

                File newFile = new File(filePathRaw + mimeExtension);
                if (!f.renameTo(newFile)) {
                    ;// Log.e(ImageSaver.class.getSimpleName(), "Can't rename file");
                }
                return filePathRaw + mimeExtension;
            } else {
                String message = "Cannot find entity for " + imageSrc;
                ;// Log.e(ImageSaver.class.getSimpleName(), message);
                throw new IllegalArgumentException();
            }
        } finally {
            if (getRequest != null) {
                getRequest.abort();
            }
        }
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void raise(SecretGifException e) {
    }

}
