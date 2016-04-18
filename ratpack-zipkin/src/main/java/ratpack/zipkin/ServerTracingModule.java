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
import com.google.inject.Provides;
import ratpack.func.Function;
import ratpack.guice.ConfigurableModule;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.zipkin.internal.ServerRequestAdapterFactory;
import ratpack.zipkin.internal.ServerResponseAdapterFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Module for ZipKin distributed tracing.
 */
public class ServerTracingModule extends ConfigurableModule<ServerTracingModule.Config> {
  @Override
  protected void configure() {
    bind(ServerTracingHandler.class);
    bind(ServerTracingDecorator.class);
    bind(ServerRequestAdapterFactory.class);
    bind(ServerResponseAdapterFactory.class);
  }

  @Provides
  public Brave getBrave(final Config config) {
    return config.brave;
  }

  @Provides
  public ServerTracer getServerTracer(final Brave brave) {
    return brave.serverTracer();
  }

  @Provides
  public LocalTracer localTracer(final Brave brave) {
    return brave.localTracer();
  }

  @Provides
  public ServerResponseInterceptor serverResponseInterceptor(final ServerTracer tracer) {
    return new ServerResponseInterceptor(tracer);
  }

  @Provides
  public ServerRequestInterceptor serverRequestInterceptor(final ServerTracer tracer) {
    return new ServerRequestInterceptor(tracer);
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

  /**
   * Creates a config instance.
   *
   * @return a config instance.
   */
  public static Config config() {
    return new Config();
  }

  /**
   * Creates a config instance with a server name.
   *
   * Will create a Config with a {@link Brave} instance already configured.
   *
   * @param serviceName the service name
   *
   * @return the config instance
   */
  public static Config config(final String serviceName) {
    return new Config().withBrave(new Brave.Builder(serviceName).build());
  }

  public static class Config {
    private Brave brave;
    private SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();
    private RequestAnnotationExtractor requestAnnotationFunc = RequestAnnotationExtractor.DEFAULT;

    private ResponseAnnotationExtractor responseAnnotationFunc = ResponseAnnotationExtractor.DEFAULT;
    private Config() {
      //no-op
    }
    public Config withBrave(final Brave brave) {
      this.brave = brave;
      return this;
    }

    public Config withSpanNameProvider(final SpanNameProvider spanNameProvider) {
      this.spanNameProvider = spanNameProvider;
      return this;
    }

    public Config withRequestAnnotations(final RequestAnnotationExtractor func) {
      this.requestAnnotationFunc = func;
      return this;
    }

    public Config withResponseAnnotations(final ResponseAnnotationExtractor func) {
      this.responseAnnotationFunc = func;
      return this;
    }
  }
}
