package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.SymmetricCipher;
import net.e6tech.elements.web.cxf.Observer;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotAuthorizedException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class AuthObserver extends Observer {
    protected static SymmetricCipher cipher = SymmetricCipher.getInstance("AES");
    private String sharedKey;
    private SecretKey secretKey;
    private long expiration = 60 * 1000L;

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
        if (sharedKey != null)
            secretKey = cipher.getKeySpec(Hex.toBytes(sharedKey));
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public void authorize(RestfulProxy proxy) {
        String token;
        try {
            token = encrypt();
        } catch (GeneralSecurityException e) {
            throw new SystemException(e);
        }
        if (token == null)
            return;
        proxy.setRequestProperty("Authorization", "SharedKey " + token);
    }

    private String encrypt() throws GeneralSecurityException {
        if (secretKey == null)
            return null;
        byte[] time =  Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
        String encrypted = cipher.encrypt(secretKey, time, null);
        return encrypted;
    }

    private long decrypt(String encrypted) throws GeneralSecurityException {
        if (secretKey == null)
            return System.currentTimeMillis();
        byte[] decrypted = cipher.decrypt(secretKey, encrypted, null);
        return Long.parseLong(new String(decrypted, StandardCharsets.UTF_8));
    }

    public void beforeInvocation(HttpServletRequest request, HttpServletResponse response, Object instance, Method method, Object[] args) {
        String auth = request.getHeader("Authorization");
        if (sharedKey != null) {
            if (auth == null)
                throw new NotAuthorizedException("Missing Authorization header");

            if (auth.startsWith("SharedKey")) {
                auth = auth.substring("SharedKey".length());
                auth = auth.trim();
                try {
                    long time = decrypt(auth);
                    if (System.currentTimeMillis() - time > expiration)
                        throw new NotAuthorizedException("Shared token expired");
                } catch (GeneralSecurityException e) {
                    throw new SystemException(e);
                }
            } else {
                throw new NotAuthorizedException("Missing SharedKey");
            }
        }
    }
}
