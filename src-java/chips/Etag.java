package chips;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;


public class Etag {
        
    public String calcSha1(File file) {
        long fileLength = file.length();

        if (fileLength <= 4 * 1024 * 1024) {
            return smallFileSha1(file);
        } else {
            return largeFileSha1(file);
        }
    }

    private String largeFileSha1(File file) {
        InputStream inputStream = null;
        try {
            MessageDigest gsha1 = MessageDigest.getInstance("SHA1");
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            inputStream = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[1024];
            int nRead = 0;
            int block = 0;
            int count = 0;
            final int BLOCK_SIZE = 4 * 1024 * 1024;
            while ((nRead = inputStream.read(buffer)) != -1) {
                count += nRead;
                sha1.update(buffer, 0, nRead);
                if (BLOCK_SIZE == count) {
                    gsha1.update(sha1.digest());
                    sha1 = MessageDigest.getInstance("SHA1");
                    block++;
                    count = 0;
                }
            }
            if (count != 0) {
                gsha1.update(sha1.digest());
                block++;
            }
            byte[] digest = gsha1.digest();

            byte[] blockBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN).putInt(block).array();

            byte[] result = ByteBuffer.allocate(4 + digest.length)
                .put(blockBytes, 0, blockBytes.length)
                .put(digest, 0, digest.length).array();

            return new String(Base64.encodeBase64URLSafe(result));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private String smallFileSha1(File file) {
        InputStream inputStream = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            inputStream = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[1024];
            int nRead = 0;
            while ((nRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, nRead);
            }
            byte[] digest = md.digest();
            byte[] blockBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN).putInt(1).array();
            byte[] result = ByteBuffer.allocate(4 + digest.length)
                .put(blockBytes, 0, 4).put(digest, 0, digest.length)
                .array();
            return new String(Base64.encodeBase64URLSafe(result));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}
