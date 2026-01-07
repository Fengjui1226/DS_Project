package app.web;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServerContext 保存伺服器共用資源
 */
public class ServerContext {

    public final SessionManager sessionManager;
    public final RateLimiter rateLimiter;
    public final String corsOrigin;
    public final String corsMethods;
    public final String corsHeaders;
    public final int shutdownTimeoutSeconds;

    public final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // 給 SimpleServer 注入
    public ExecutorService executor;

    public ServerContext(SessionManager sessionManager,
                         RateLimiter rateLimiter,
                         String corsOrigin,
                         String corsMethods,
                         String corsHeaders,
                         int shutdownTimeoutSeconds) {
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.corsOrigin = corsOrigin;
        this.corsMethods = corsMethods;
        this.corsHeaders = corsHeaders;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }
}
