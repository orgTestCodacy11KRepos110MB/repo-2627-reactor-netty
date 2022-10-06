/*
 * Copyright (c) 2019-2022 VMware, Inc. or its affiliates, All Rights Reserved.
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
package reactor.netty5.http.client;

import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpSetCookie;
import reactor.netty5.http.HttpInfos;
import reactor.util.annotation.Nullable;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.Set;

/**
 * An Http Reactive Channel with several accessors related to HTTP flow: resource URL,
 * information for redirections etc...
 *
 * @since 0.9.3
 */
public interface HttpClientInfos extends HttpInfos {

	/**
	 * Returns resolved HTTP cookies.
	 *
	 * @return Resolved HTTP cookies
	 */
	Map<CharSequence, Set<HttpSetCookie>> cookies();

	/**
	 * Return the current {@link ContextView} associated with the Mono/Flux exposed
	 * via {@link HttpClient.ResponseReceiver#response()} or related terminating API.
	 *
	 * @return the current user {@link ContextView}
	 * @since 1.0.0
	 */
	ContextView currentContextView();

	/**
	 * Return the previous redirections or empty array
	 *
	 * @return the previous redirections or empty array
	 */
	String[] redirectedFrom();

	/**
	 * Return outbound headers to be sent
	 *
	 * @return outbound headers to be sent
	 */
	HttpHeaders requestHeaders();

	/**
	 * Return the fully qualified URL of the requested resource. In case of redirects, return the URL the last
	 * redirect led to.
	 *
	 * @return The URL of the retrieved resource. This method can return null in case there was an error before the
	 * client could create the URL
	 */
	@Nullable
	String resourceUrl();
}