package eu.xfsc.fc.server.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Defines callback methods to customize the Java-based configuration for Spring MVC.
 * Custom implementation of the {@link WebMvcConfigurer}.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  private static final MediaType SPARQL_QUERY = new MediaType("application", "sparql-query");
  private static final MediaType OPENCYPHER_QUERY = new MediaType("application", "opencypher-query");

  /**
   * Register query-language media types so Spring can deserialize their request bodies as String.
   *
   * <h3>Bug: 415 Unsupported Media Type on POST /query</h3>
   *
   * The OpenAPI spec (fc_openapi.yaml) defines {@code application/opencypher-query} and
   * {@code application/sparql-query} as Content-Types for {@code POST /query}. The OpenAPI
   * generator correctly produces {@code @RequestMapping(consumes = {"application/sparql-query",
   * "application/opencypher-query"})} on QueryApi, so Spring's handler mapping accepts the
   * request. However, Spring's content negotiation has a <b>second</b> check: it must find an
   * {@link HttpMessageConverter} whose {@code supportedMediaTypes} include the incoming
   * Content-Type. The default {@link StringHttpMessageConverter} only declares
   * {@code text/plain} and {@code * /*}. In practice Spring does not match {@code * /*} for
   * custom application/* types, so no converter is found and Spring returns 415 before the
   * request body is ever read.
   *
   * <p>MockMvc-based JUnit tests (QueryControllerTest) do NOT trigger this bug because MockMvc
   * bypasses the real Servlet container's content negotiation pipeline. The bug only manifests
   * when running against the actual embedded Tomcat (docker compose, integration tests).
   *
   * <p>Fix: explicitly add the two query media types to StringHttpMessageConverter so Spring
   * can deserialize the raw query text as a {@code String @RequestBody}.
   *
   * @see eu.xfsc.fc.server.service.QueryLanguageProperties
   */
  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof StringHttpMessageConverter stringConverter) {
        List<MediaType> types = new ArrayList<>(stringConverter.getSupportedMediaTypes());
        types.add(SPARQL_QUERY);
        types.add(OPENCYPHER_QUERY);
        stringConverter.setSupportedMediaTypes(types);
        return;
      }
    }
  }

  /**
   * Defines cross-origin resource sharing.
   */
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*")
          .allowedMethods("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS");
      }
    };
  }

  /**
   * Defines firewall settings.
   */
  @Bean
  public HttpFirewall configureFirewall() {
    StrictHttpFirewall strictHttpFirewall = new StrictHttpFirewall();
    strictHttpFirewall.setAllowUrlEncodedDoubleSlash(true);
    strictHttpFirewall.setAllowUrlEncodedSlash(true);
    strictHttpFirewall.setAllowUrlEncodedPercent(true);
    strictHttpFirewall.setAllowUrlEncodedPeriod(true);
    strictHttpFirewall.setAllowSemicolon(true);
    return strictHttpFirewall;
  }

  /**
   * Defines RegistrationsHandlerMapping settings.
   */
  @Bean
  public WebMvcRegistrations webMvcRegistrationsHandlerMapping() {
    return new WebMvcRegistrations() {
        @Override
        public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
            RequestMappingHandlerMapping mapper = new RequestMappingHandlerMapping();
            mapper.setUrlDecode(false);
            //mapper.setUrlPathHelper(UrlPathHelper.defaultInstance.setUrlDecode(false));
            return mapper;
        }
    };
  }
  
  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
      //log.info("Configuring Tomcat to allow encoded slashes.");
      return factory -> factory.addConnectorCustomizers(connector -> connector.setEncodedSolidusHandling(
              EncodedSolidusHandling.DECODE.getValue()));
  }  
    
}