/*
 * Copyright (c) 2018-2022 VMware, Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.netty5.resources;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.function.Supplier;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.resolver.AddressResolverGroup;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Operators;
import reactor.netty5.ChannelBindException;
import reactor.netty5.Connection;
import reactor.netty5.ConnectionObserver;
import reactor.netty5.internal.util.Metrics;
import reactor.netty5.transport.AddressUtils;
import reactor.netty5.transport.TransportConfig;
import reactor.netty5.transport.TransportConnector;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import static reactor.netty5.ReactorNetty.format;

/**
 * @author Stephane Maldini
 * @author Violeta Georgieva
 */
final class NewConnectionProvider implements ConnectionProvider {

	final static Logger log = Loggers.getLogger(NewConnectionProvider.class);

	final static NewConnectionProvider INSTANCE = new NewConnectionProvider();

	@Override
	public Mono<? extends Connection> acquire(TransportConfig config,
			ConnectionObserver observer,
			@Nullable Supplier<? extends SocketAddress> remoteAddress,
			@Nullable AddressResolverGroup<?> resolverGroup) {
		return Mono.create(sink -> {
			SocketAddress remote = null;
			if (remoteAddress != null) {
				remote = Objects.requireNonNull(remoteAddress.get(), "Remote Address supplier returned null");
			}

			ConnectionObserver connectionObserver = new NewConnectionObserver(sink, observer);
			DisposableConnect disposableConnect = new DisposableConnect(sink, config.bindAddress());
			if (remote != null && resolverGroup != null) {
				ChannelInitializer<Channel> channelInitializer = config.channelInitializer(connectionObserver, remote, false);
				Context currentContext = Context.of(sink.contextView());
				if (config.metricsRecorder() != null && Metrics.isMicrometerAvailable()) {
					Object currentObservation = reactor.netty5.Metrics.currentObservation(currentContext);
					if (currentObservation != null) {
						currentContext = reactor.netty5.Metrics.updateContext(currentContext, currentObservation);
					}
				}
				TransportConnector.connect(config, remote, resolverGroup, channelInitializer, currentContext)
				                  .subscribe(disposableConnect);
			}
			else {
				Objects.requireNonNull(config.bindAddress(), "bindAddress");
				SocketAddress local = Objects.requireNonNull(config.bindAddress().get(), "Bind Address supplier returned null");
				if (local instanceof InetSocketAddress localInet) {

					if (localInet.isUnresolved()) {
						local = AddressUtils.createResolved(localInet.getHostName(), localInet.getPort());
					}
				}
				ChannelInitializer<Channel> channelInitializer = config.channelInitializer(connectionObserver, null, true);
				TransportConnector.bind(config, channelInitializer, local, local instanceof DomainSocketAddress)
				                  .subscribe(disposableConnect);
			}
		});
	}

	@Override
	public boolean isDisposed() {
		return false;
	}

	@Override
	public int maxConnections() {
		return 1;
	}

	static final class DisposableConnect implements CoreSubscriber<Channel>, Disposable {
		final MonoSink<Connection> sink;
		final Context currentContext;
		final Supplier<? extends SocketAddress> bindAddress;

		Subscription subscription;

		DisposableConnect(MonoSink<Connection> sink, @Nullable Supplier<? extends SocketAddress> bindAddress) {
			this.sink = sink;
			this.currentContext = Context.of(sink.contextView());
			this.bindAddress = bindAddress;
		}

		@Override
		public Context currentContext() {
			return currentContext;
		}

		@Override
		public void dispose() {
			subscription.cancel();
		}

		@Override
		public void onComplete() {
		}

		@Override
		public void onError(Throwable t) {
			if (bindAddress != null && (t instanceof BindException ||
					// With epoll/kqueue transport it is
					// io.netty5.channel.unix.Errors$NativeIoException: bind(..) failed: Address already in use
					(t instanceof IOException && t.getMessage() != null &&
							t.getMessage().contains("bind(..)")))) {
				sink.error(ChannelBindException.fail(bindAddress.get(), null));
			}
			else {
				sink.error(t);
			}
		}

		@Override
		public void onNext(Channel channel) {
			if (log.isDebugEnabled()) {
				log.debug(format(channel, "Connected new channel"));
			}
		}

		@Override
		public void onSubscribe(Subscription s) {
			if (Operators.validate(subscription, s)) {
				this.subscription = s;
				sink.onCancel(this);
				s.request(Long.MAX_VALUE);
			}
		}
	}

	static final class NewConnectionObserver implements ConnectionObserver {

		final MonoSink<Connection> sink;
		final Context              currentContext;
		final ConnectionObserver   obs;

		NewConnectionObserver(MonoSink<Connection> sink, ConnectionObserver obs) {
			this.sink = sink;
			this.currentContext = Context.of(sink.contextView());
			this.obs = obs;
		}

		@Override
		public Context currentContext() {
			return currentContext;
		}

		@Override
		public void onStateChange(Connection connection, State newState) {
			if (log.isDebugEnabled()) {
				log.debug(format(connection.channel(), "onStateChange({}, {})"), newState, connection);
			}
			if (newState == State.CONFIGURED) {
				sink.success(connection);
			}
			else if (newState == State.DISCONNECTING && connection.channel()
			                                                      .isActive()) {
				connection.channel()
				          .close();
			}
			obs.onStateChange(connection, newState);
		}

		@Override
		public void onUncaughtException(Connection c, Throwable error) {
			sink.error(error);
			obs.onUncaughtException(c, error);
		}
	}
}
