package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.InterceptorListener;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.network.restful.RestfulProxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HailingFrequency {
    static Logger logger = Logger.getLogger();
    private Member member;
    private Map<Class, Object> services = new ConcurrentHashMap<>();
    private Collective collective;

    public HailingFrequency(Member member, Collective federation) {
        this.collective = federation;
        setMember(member);
    }

    public HailingFrequency(String hostAddress, Collective federation) {
        this.collective = federation;
        Member m = new Member();
        m.setMemberId("seed");
        m.setAddress(hostAddress);
        setMember(m);
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;

        for (String serviceClass : member.getServices()) {
            createRemoteService(serviceClass);
        }
    }

    @SuppressWarnings("unchecked")
    private Object createRemoteService(String serviceClass) {
        RestfulProxy proxy = new RestfulProxy(member.getAddress());
        try {
            Class cls = getClass().getClassLoader().loadClass(serviceClass);
            MyInterceptorListener listener = new MyInterceptorListener();
            listener.proxy = proxy;
            Object service = proxy.newProxy(cls, listener);
            services.put(cls, service);
            proxy.getClient().setReadTimeout(collective.getReadTimeout());
            proxy.getClient().setConnectionTimeout(collective.getConnectionTimeout());
            return service;
        } catch (ClassNotFoundException e) {
            // warn
            return null;
        }
    }

    public BeaconAPI beacon() {
        return getService(BeaconAPI.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> cls) {
        if (cls.equals(BeaconAPI.class) && !services.containsKey(cls))
            return (T) createRemoteService(BeaconAPI.class.getName());

        return (T) services.get(cls);
    }

    @SuppressWarnings("unchecked")
    public <T> T removeService(Class<T> cls) {
        return (T) services.remove(cls);
    }

    public String memberId() {
        return member.getMemberId();
    }

    private class MyInterceptorListener implements InterceptorListener {
        RestfulProxy proxy;

        public void preInvocation(CallFrame frame) {
            AuthObserver observer = collective.getAuthObserver();
            if (observer == null)
                return;
            observer.authorize(proxy);
        }

        @Override
        public Object onException(CallFrame frame, Throwable throwable) {
            logger.warn("Error executing " + frame.getMethod().getName() + " memberId=" + member.getMemberId() + " address=" + member.getAddress() , throwable);
            throw new SystemException(throwable);
        }
    }
}
