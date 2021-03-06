/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.transport.stomp;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

public class StompConnection {

    public static final long RECEIVE_TIMEOUT = 10000;

    private Socket stompSocket;
    private ByteArrayOutputStream inputBuffer = new ByteArrayOutputStream();
    private String version = Stomp.DEFAULT_VERSION;

    public void open(String host, int port) throws IOException, UnknownHostException {
        open(new Socket(host, port));
    }

    public void open(Socket socket) {
        stompSocket = socket;
    }

    public void close() throws IOException {
        if (stompSocket != null) {
            stompSocket.close();
            stompSocket = null;
        }
    }

    public void sendFrame(String data) throws Exception {
        byte[] bytes = data.getBytes("UTF-8");
        OutputStream outputStream = stompSocket.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    public void sendFrame(String frame, byte[] data) throws Exception {
        byte[] bytes = frame.getBytes("UTF-8");
        OutputStream outputStream = stompSocket.getOutputStream();
        outputStream.write(bytes);
        outputStream.write(data);
        outputStream.flush();
    }

    public StompFrame receive() throws Exception {
        return receive(RECEIVE_TIMEOUT);
    }

    public StompFrame receive(long timeOut) throws Exception {
        stompSocket.setSoTimeout((int)timeOut);
        InputStream is = stompSocket.getInputStream();
        StompWireFormat wf = new StompWireFormat();
        wf.setStompVersion(version);
        DataInputStream dis = new DataInputStream(is);
        return (StompFrame)wf.unmarshal(dis);
    }

    public String receiveFrame() throws Exception {
        return receiveFrame(RECEIVE_TIMEOUT);
    }

    public String receiveFrame(long timeOut) throws Exception {
        stompSocket.setSoTimeout((int)timeOut);
        InputStream is = stompSocket.getInputStream();
        int c = 0;
        for (;;) {
            c = is.read();
            if (c < 0) {
                throw new IOException("socket closed.");
            } else if (c == 0) {
                c = is.read();
                if (c == '\n') {
                    // end of frame
                    return stringFromBuffer(inputBuffer);
                } else {
                    inputBuffer.write(0);
                    inputBuffer.write(c);
                }
            } else {
                inputBuffer.write(c);
            }
        }
    }

    private String stringFromBuffer(ByteArrayOutputStream inputBuffer) throws Exception {
        byte[] ba = inputBuffer.toByteArray();
        inputBuffer.reset();
        return new String(ba, "UTF-8");
    }

    public Socket getStompSocket() {
        return stompSocket;
    }

    public void setStompSocket(Socket stompSocket) {
        this.stompSocket = stompSocket;
    }

    public void connect(String username, String password) throws Exception {
        connect(username, password, null);
    }

    public void connect(String username, String password, String client) throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("login", username);
        headers.put("passcode", password);
        if (client != null) {
            headers.put("client-id", client);
        }
        connect(headers);
    }

    public void connect(HashMap<String, String> headers) throws Exception {
        StompFrame frame = new StompFrame("CONNECT", headers);
        sendFrame(frame.format());

        StompFrame connect = receive();
        if (!connect.getAction().equals(Stomp.Responses.CONNECTED)) {
            throw new Exception ("Not connected: " + connect.getBody());
        }
    }

    public void disconnect() throws Exception {
        disconnect(null);
    }

    public void disconnect(String receiptId) throws Exception {
        StompFrame frame = new StompFrame("DISCONNECT");
        if (receiptId != null && !receiptId.isEmpty()) {
            frame.getHeaders().put(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
        }
        sendFrame(frame.format());
    }

    public void send(String destination, String message) throws Exception {
        send(destination, message, null, null);
    }

    public void send(String destination, String message, String transaction, HashMap<String, String> headers) throws Exception {
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        headers.put("destination", destination);
        if (transaction != null) {
            headers.put("transaction", transaction);
        }
        StompFrame frame = new StompFrame("SEND", headers, message.getBytes());
        sendFrame(frame.format());
    }

    public void subscribe(String destination) throws Exception {
        subscribe(destination, null, null);
    }

    public void subscribe(String destination, String ack) throws Exception {
        subscribe(destination, ack, new HashMap<String, String>());
    }

    public void subscribe(String destination, String ack, HashMap<String, String> headers) throws Exception {
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        headers.put("destination", destination);
        if (ack != null) {
            headers.put("ack", ack);
        }
        StompFrame frame = new StompFrame("SUBSCRIBE", headers);
        sendFrame(frame.format());
    }

    public void unsubscribe(String destination) throws Exception {
        unsubscribe(destination, null);
    }

    public void unsubscribe(String destination, HashMap<String, String> headers) throws Exception {
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        headers.put("destination", destination);
        StompFrame frame = new StompFrame("UNSUBSCRIBE", headers);
        sendFrame(frame.format());
    }

    public void begin(String transaction) throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("transaction", transaction);
        StompFrame frame = new StompFrame("BEGIN", headers);
        sendFrame(frame.format());
    }

    public void abort(String transaction) throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("transaction", transaction);
        StompFrame frame = new StompFrame("ABORT", headers);
        sendFrame(frame.format());
    }

    public void commit(String transaction) throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("transaction", transaction);
        StompFrame frame = new StompFrame("COMMIT", headers);
        sendFrame(frame.format());
    }

    public void ack(StompFrame frame) throws Exception {
        ack(frame.getHeaders().get("message-id"), null);
    }

    public void ack(StompFrame frame, String transaction) throws Exception {
        ack(frame.getHeaders().get("message-id"), transaction);
    }

    public void ack(String messageId) throws Exception {
        ack(messageId, null);
    }

    public void ack(String messageId, String transaction) throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("message-id", messageId);
        if (transaction != null)
            headers.put("transaction", transaction);
        StompFrame frame = new StompFrame("ACK", headers);
        sendFrame(frame.format());
    }

    public void keepAlive() throws Exception {
        OutputStream outputStream = stompSocket.getOutputStream();
        outputStream.write('\n');
        outputStream.flush();
    }

    protected String appendHeaders(HashMap<String, Object> headers) {
        StringBuilder result = new StringBuilder();
        for (String key : headers.keySet()) {
            result.append(key + ":" + headers.get(key) + "\n");
        }
        result.append("\n");
        return result.toString();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
