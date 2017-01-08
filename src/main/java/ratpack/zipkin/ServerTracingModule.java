/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ratpack.zipkin;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.guice.ConfigurableModule;
import ratpack.handling.HandlerDecorator;
import ratpack.http.client.HttpClientRequestInterceptor;
import ratpack.http.client.HttpClientResponseInterceptor;
import ratpack.server.ServerConfig;
import ratpack.zipkin.internal.*;
import zipkin.Span;
import zipkin.reporter.Reporter;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import static com.google.inject.Scopes.SINGLETON;

/**
 * Module for ZipKin distributed tracing.
 */
public class ServerTracingModule extends ConfigurableModule<ServerTracingModule.Config> {
  @Override
  protected void configure() {
    bind(ServerRequestAdapterFactory.class).in(SINGLETON);
    bind(ServerResponseAdapterFactory.class).in(SINGLETON);
    bind(ServerTracingHandler.class).to(DefaultServerTracingHandler.class);
    Provider<ServerTracingHandler> serverTracingHandlerProviderProvider = getProvider(ServerTracingHandler.class);

    Multibinder.newSetBinder(binder(), HttpClientRequestInterceptor.class)
               .addBinding().toInstance(req -> logger.info("Request intercepted: {}", req));
//    Multibinder.newSetBinder(binder(), HttpClientRequestInterceptor.class)
//               .addBinding().to(ZipkinClientRequestInterceptor.class);
    Multibinder.newSetBinder(binder(), HttpClientResponseInterceptor.class)
               .addBinding().toInstance(res -> logger.info("Response intercepted: {}", res));
//    Multibinder.newSetBinder(binder(), HttpClientResponseInterceptor.class)
//               .addBinding().to(ZipkinClientResponseInterceptor.class);
    bind(ClientRequestAdapterFactory.class).in(SINGLETON);
    bind(ClientResponseAdapterFactory.class).in(SINGLETON);

    Multibinder.newSetBinder(binder(), HandlerDecorator.class).
        addBinding().toProvider(() -> HandlerDecorator.prepend(serverTracingHandlerProviderProvider.get()));
  }

  @Provides
  public SpanNameProvider spanNameProvider(final Config config) {
    return config.spanNameProvider;
  }

  @Provides
  public RequestAnnotationExtractor requestAnnotationExtractorFunc(final Config config) {
    return config.requestAnnotationFunc;
  }

  @Provides
  public ResponseAnnotationExtractor responseAnnotationExtractorFunc(final Config config) {
    return config.responseAnnotationFunc;
  }

  @Provides
  public ServerResponseInterceptor serverResponseInterceptor(final Brave brave) {
    return new ServerResponseInterceptor(brave.serverTracer());
  }

  @Provides
  public ServerRequestInterceptor serverRequestInterceptor(final Brave brave) {
    return new ServerRequestInterceptor(brave.serverTracer());
  }

  @Provides
  public ClientRequestInterceptor clientRequestInterceptor(final Brave brave) {
    return new ClientRequestInterceptor(brave.clientTracer());
  }

  @Provides
  public ClientResponseInterceptor clientResponseInterceptor(final Brave brave) {
    return new ClientResponseInterceptor(brave.clientTracer());
  }

  @Provides
  public Brave getBrave(final Config config, final ServerConfig serverConfig) {
    Brave.Builder braveBuilder = new Brave.Builder(
        new RatpackServerClientLocalSpanState(
            config.serviceName,
            ByteBuffer.wrap(
                serverConfig.getAddress() != null
                ?
                serverConfig.getAddress().getAddress()
                :
                InetAddress.getLoopbackAddress().getAddress()
            ).getInt(),
            serverConfig.getPort()
        )
    );
    if (config.spanReporter != null) {
      braveBuilder.reporter(config.spanReporter);
    }
    else if (config.spanCollector != null) {
      braveBuilder.spanCollector(config.spanCollector);
    }
    if (config.sampler != null) {
      braveBuilder.traceSampler(config.sampler);
    }
    return braveBuilder.build();
  }

  @Provides
  public LocalTracer localTracer(final Brave brave) {
    return brave.localTracer();
  }

  /**
   * Configuration class for {@link ServerTracingModule}.
   */
  public static class Config {
    private String serviceName = "unknown";
    private SpanCollector spanCollector;
    private Reporter<Span> spanReporter;
    private Sampler sampler;
    private SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();
    private RequestAnnotationExtractor requestAnnotationFunc = RequestAnnotationExtractor.DEFAULT;
    private ResponseAnnotationExtractor responseAnnotationFunc = ResponseAnnotationExtractor.DEFAULT;

    public Config serviceName(final String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /**
     * Configure the module to use the specified {@link SpanCollector}.
     *
     * @param spanCollector the span collector
     *
     * @return the Config instance
     * @deprecated {@link SpanCollector} was deprecated in Brave 3.14.0.
     *
     * Use {@link ServerTracingModule.Config#spanReporter(Reporter)} instead.
     */
    @Deprecated
    public Config spanCollector(final SpanCollector spanCollector) {
      this.spanCollector = spanCollector;
      return this;
    }

    public Config spanReporter(final Reporter<Span> reporter) {
      this.spanReporter = reporter;
      return this;
    }

    public Config sampler(final Sampler sampler) {
      this.sampler = sampler;
      return this;
    }
    public Config spanNameProvider(final SpanNameProvider spanNameProvider) {
      this.spanNameProvider = spanNameProvider;
      return this;
    }

    public Config requestAnnotations(final RequestAnnotationExtractor func) {
      this.requestAnnotationFunc = func;
      return this;
    }

    public Config responseAnnotations(final ResponseAnnotationExtractor func) {
      this.responseAnnotationFunc = func;
      return this;
    }

  }
}
