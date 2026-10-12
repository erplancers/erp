package jakarta.servlet;

/**
 * Compatibility marker for Spark 4.0.1's shaded Jetty, which still links
 * against this Servlet API type even though Boot 4 uses a newer Servlet API
 * where it was removed. Test-only: production code does not use this type.
 */
@Deprecated
public interface SingleThreadModel {
}
