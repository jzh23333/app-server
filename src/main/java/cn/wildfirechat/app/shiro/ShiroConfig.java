package cn.wildfirechat.app.shiro;


import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.Cookie;
import org.apache.shiro.web.servlet.ShiroHttpSession;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.crazycake.shiro.IRedisManager;
import org.crazycake.shiro.RedisCacheManager;
import org.crazycake.shiro.RedisManager;
import org.crazycake.shiro.RedisSessionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShiroConfig {
    @Resource
    LettuceConnectionFactory lettuceConnectionFactory;

    @Autowired
    private PhoneCodeRealm phoneCodeRealm;

    @Autowired
    private ScanCodeRealm scanCodeRealm;

    @Autowired
    private UserPasswordRealm userPasswordRealm;

    @Value("${wfc.all_client_support_ssl}")
    private boolean All_Client_Support_SSL;

    @Bean(name = "shiroFilter")
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);
        shiroFilterFactoryBean.setLoginUrl("/login");
        shiroFilterFactoryBean.setUnauthorizedUrl("/notRole");
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

        // <!-- authc:所有url都必须认证通过才可以访问; anon:所有url都都可以匿名访问-->
        filterChainDefinitionMap.put("/send_code", "anon");
        filterChainDefinitionMap.put("/login", "anon");
        filterChainDefinitionMap.put("/pc_session", "anon");
        filterChainDefinitionMap.put("/amr2mp3", "anon");

        filterChainDefinitionMap.put("/login_pwd", "anon");
        filterChainDefinitionMap.put("/send_reset_code", "anon");
        filterChainDefinitionMap.put("/reset_pwd", "anon");
        filterChainDefinitionMap.put("/session_login/**", "anon");
        filterChainDefinitionMap.put("/user/online_event", "anon");
        filterChainDefinitionMap.put("/logs/**", "anon");
        filterChainDefinitionMap.put("/im_event/**", "anon");
        filterChainDefinitionMap.put("/im_exception_event/**", "anon");
        filterChainDefinitionMap.put("/message/censor", "anon");
        filterChainDefinitionMap.put("/", "anon");

        filterChainDefinitionMap.put("/confirm_pc", "login");
        filterChainDefinitionMap.put("/cancel_pc", "login");
        filterChainDefinitionMap.put("/scan_pc/**", "login");
        filterChainDefinitionMap.put("/put_group_announcement", "login");
        filterChainDefinitionMap.put("/get_group_announcement", "login");
        filterChainDefinitionMap.put("/things/add_device", "login");
        filterChainDefinitionMap.put("/things/list_device", "login");

        //主要这行代码必须放在所有权限设置的最后，不然会导致所有 url 都被拦截 剩余的都需要认证
        filterChainDefinitionMap.put("/**", "login");
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        shiroFilterFactoryBean.getFilters().put("login", new JsonAuthLoginFilter());
        return shiroFilterFactoryBean;

    }

    @Bean("securityManager")
    public SecurityManager securityManager() {
        DefaultWebSecurityManager defaultSecurityManager = new DefaultWebSecurityManager();
        defaultSecurityManager.setRealms(Arrays.asList(phoneCodeRealm, scanCodeRealm, userPasswordRealm));
        defaultSecurityManager.setSessionManager(sessionManager());
        defaultSecurityManager.setCacheManager(redisCacheManager());
        SecurityUtils.setSecurityManager(defaultSecurityManager);
        return defaultSecurityManager;
    }

    private ShiroSessionManager sessionManager() {
        ShiroSessionManager sessionManager = new ShiroSessionManager();
        sessionManager.setGlobalSessionTimeout(Long.MAX_VALUE);
        sessionManager.setSessionDAO(redisSessionDAO());
        Cookie cookie = new SimpleCookie(ShiroHttpSession.DEFAULT_SESSION_ID_NAME);
        if (All_Client_Support_SSL) {
            cookie.setSameSite(Cookie.SameSiteOptions.NONE);
            cookie.setSecure(true);
        } else {
            cookie.setSameSite(null);
        }
        cookie.setMaxAge(Integer.MAX_VALUE);
        sessionManager.setSessionIdCookie(cookie);
        sessionManager.setSessionIdCookieEnabled(true);
        sessionManager.setSessionIdUrlRewritingEnabled(true);
        return sessionManager;
    }

    public RedisSessionDAO redisSessionDAO() {
        RedisSessionDAO redisSessionDAO = new RedisSessionDAO();
        redisSessionDAO.setRedisManager(redisManager());
        return redisSessionDAO;
    }

    /**
     * cacheManager 缓存 redis实现
     * 使用的是shiro-redis开源插件
     *
     * @return
     */
    public RedisCacheManager redisCacheManager() {
        RedisCacheManager redisCacheManager = new RedisCacheManager();
        redisCacheManager.setRedisManager(redisManager());
        //redis中针对不同用户缓存(此处的id需要对应user实体中的id字段,用于唯一标识)
        redisCacheManager.setPrincipalIdFieldName("id");
        //用户权限信息缓存时间
        redisCacheManager.setExpire(200000);
        return redisCacheManager;
    }

    /**
     * 配置shiro redisManager
     * 使用的是shiro-redis开源插件
     *
     * @return
     */
    @Bean
    public IRedisManager redisManager() {
        IRedisManager manager;
        RedisManager redisManager = new RedisManager();
        redisManager.setHost(lettuceConnectionFactory.getHostName());
        redisManager.setPort(lettuceConnectionFactory.getPort());
        redisManager.setDatabase(lettuceConnectionFactory.getDatabase());
        redisManager.setTimeout(0);
        if (!StringUtils.isEmpty(lettuceConnectionFactory.getPassword())) {
            redisManager.setPassword(lettuceConnectionFactory.getPassword());
        }
        manager = redisManager;
        return manager;
    }
}