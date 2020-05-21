package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.InterceptorListener;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.security.SymmetricCipher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HailingFrequency {
    protected static SymmetricCipher cipher = SymmetricCipher.getInstance("AES");

    private Member member;
    private Map<Class, Object> services = new ConcurrentHashMap<>();
    private RestfulProxy proxy;
    private AuthObserver observer;
    private MyInterceptorListener listener = new MyInterceptorListener();
    private int connectionTimeout = 15000; // 15 seconds
    private int readTimeout = 10000; // 10 seconds

    public HailingFrequency() {
    }

    public HailingFrequency(Member member, AuthObserver observer) {
        setMember(member);
        this.observer = observer;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public HailingFrequency connectionTimeout(int connectionTimeout) {
        setConnectionTimeout(connectionTimeout);
        return this;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public HailingFrequency readTimeout(int readTimeout) {
        setReadTimeout(readTimeout);
        return this;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
        if (proxy == null || !proxy.getClient().getAddress().equals(member.getHostAddress())) {
            proxy = new RestfulProxy(member.getHostAddress());
            services.put(BeaconAPI.class, proxy.newProxy(BeaconAPI.class, listener));
            proxy.getClient().setReadTimeout(readTimeout);
            proxy.getClient().setConnectionTimeout(connectionTimeout);
        }
    }

    public BeaconAPI beacon() {
        return getService(BeaconAPI.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> cls) {
        return (T) services.computeIfAbsent(cls, serviceClass -> proxy.newProxy(cls, listener));
    }

    @SuppressWarnings("unchecked")
    public <T> T removeService(Class<T> cls) {
        return (T) services.remove(cls);
    }

    public String memberId() {
        return member.getMemberId();
    }

    private class MyInterceptorListener implements InterceptorListener {

        public void preInvocation(CallFrame frame) {
            if (observer == null)
                return;
            observer.authorize(proxy);
        }

        @Override
        public Object onException(CallFrame frame, Throwable throwable) {
            throw new SystemException(throwable);
        }
    }
}
