package app.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 *
 * @author nsoushi
 */
@ConfigurationProperties("webpush")
class AppProperties {

    var serverUrl: String?= null
    var serverKey: String? = null
}