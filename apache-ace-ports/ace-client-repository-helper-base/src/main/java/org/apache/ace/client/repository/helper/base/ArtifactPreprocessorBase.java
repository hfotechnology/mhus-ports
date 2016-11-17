/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.client.repository.helper.base;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.connectionfactory.ConnectionFactory;

/**
 * This class can be used as a base class for artifact preprocessors. It comes with its
 * own upload() method, which will be used by all artifact preprocessors anyway.
 */
public abstract class ArtifactPreprocessorBase implements ArtifactPreprocessor {

    protected static final int BUFFER_SIZE = 4 * 1024;
    
    protected final ConnectionFactory m_connectionFactory;

    /**
     * @param connectionFactory
     */
    protected ArtifactPreprocessorBase(ConnectionFactory connectionFactory) {
        m_connectionFactory = connectionFactory;
    }

    /**
     * Uploads an artifact to an OBR.
     * 
     * @param input A inputstream from which the artifact can be read.
     * @param name The name of the artifact. If the name is not unique, an IOException will be thrown.
     * @param obrBase The base URL of the obr to which this artifact should be written.
     * @return A URL to the uploaded artifact; this is identical to calling <code>determineNewUrl(name, obrBase)</code>
     * @throws IOException If there was an error reading from <code>input</code>, or if there was a problem communicating
     *         with the OBR.
     */
    protected URL upload(InputStream input, String name, URL obrBase) throws IOException {
        if (obrBase == null) {
            throw new IOException("There is no storage available for this artifact.");
        }
        if ((name == null) || (input == null)) {
            throw new IllegalArgumentException("None of the parameters can be null.");
        }

        URL url = null;
        try {
            url = determineNewUrl(name, obrBase);

            if ("file".equals(url.getProtocol())) {
                uploadToFile(input, url);
            }
            else {
                uploadToRemote(input, url);
            }
        }
        catch (IOException ioe) {
            throw new IOException("Error uploading " + name + ": " + ioe.getMessage());
        }
        finally {
            silentlyClose(input);
        }

        return url;
    }

    /**
     * Gets a stream to write an artifact to, which will be uploaded to the OBR.
     * 
     * @param name The name of the artifact.
     * @param obrBase The base URL of the obr to which this artifact should be written.
     * @return An outputstream, to which the artifact can be written.
     * @throws IOException If there is a problem setting up the outputstream.
     */
    protected OutputStream upload(final String name, final URL obrBase) throws IOException {
        /*
         * This function works by starting a thread which reads from the outputstream which is passed out,
         * and writing it to another stream, which is read by a thread that does the Upload.
         */
        final PipedOutputStream externalOutput = new PipedOutputStream();
        final PipedInputStream externalInput = new PipedInputStream(externalOutput);

        final PipedOutputStream internalOutput = new PipedOutputStream();
        final PipedInputStream internalInput = new PipedInputStream(internalOutput);

        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    for (int count = externalInput.read(buffer); count != -1; count = externalInput.read(buffer)) {
                        internalOutput.write(buffer, 0, count);
                    }
                }
                catch (IOException e) {
                    // We cannot signal this to the user, but he will notice (in the original thread)
                    // that the pipe has been broken.
                    e.printStackTrace();
                }
                finally {
                    silentlyClose(internalOutput);
                    silentlyClose(externalInput);
                }
            }
        }, "upload-Outputstream(" + name + ")").start();

        new Thread(new Runnable() {
            public void run() {
                try {
                    upload(internalInput, name, obrBase);
                }
                catch (IOException e) {
                    // We cannot signal this to the user, but he will notice (in the original thread)
                    // that the pipe has been broken.
                    e.printStackTrace();
                }
                finally {
                    silentlyClose(internalInput);
                    silentlyClose(externalOutput);
                }
            }
        }, "upload-Inputstream(" + name + ")").start();

        return externalOutput;
    }

    protected URL determineNewUrl(String name, URL obrBase) throws MalformedURLException {
        return new URL(obrBase, name);
    }

    public abstract String preprocess(String url, PropertyResolver props, String targetID, String version, URL obrBase) throws IOException;

    public abstract boolean needsNewVersion(String url, PropertyResolver props, String targetID, String fromVersion);

    /**
     * @param closable
     * @throws IOException
     */
    protected final void silentlyClose(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            }
            catch (IOException e) {
                // Ignore; nothing we can/will do about here...
            }
        }
    }

    /**
     * Uploads an artifact to a local file location.
     * 
     * @param input the input stream of the (local) artifact to upload.
     * @param url the URL of the (file) artifact to upload to.
     * @throws IOException in case of I/O problems.
     */
    private void uploadToFile(InputStream input, URL url) throws IOException {
        File file;
        try {
            file = new File(url.toURI());
        }
        catch (URISyntaxException e) {
            file = new File(url.getPath());
        }

        OutputStream output = null;

        try {
            output = new FileOutputStream(file);

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
        }
        finally {
            silentlyClose(output);
        }
    }

    /**
     * Uploads an artifact to a remote location.
     * 
     * @param input the input stream of the (local) artifact to upload.
     * @param url the URL of the (remote) artifact to upload to.
     * @throws IOException in case of I/O problems, or when the upload was refused by the remote.
     */
    private void uploadToRemote(InputStream input, URL url) throws IOException {
        OutputStream output = null;

        try {
            URLConnection connection = m_connectionFactory.createConnection(url);

            connection.setDoOutput(true);
            connection.setDoInput(true);
            output = connection.getOutputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int count = input.read(buffer); count != -1; count = input.read(buffer)) {
                output.write(buffer, 0, count);
            }
            output.close();

            if (connection instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) connection).getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new IOException("Artifact already exists in storage.");
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("The storage server returned an internal server error.");
                    default:
                        throw new IOException("The storage server returned code " + responseCode + " writing to "
                            + url.toString());
                }
            }
        }
        finally {
            silentlyClose(output);
        }
    }
}
