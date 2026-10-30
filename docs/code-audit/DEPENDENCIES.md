# 추적된 외부 패키지

crawler-service/node_modules의 package.json에 기록된 이름·버전·설명을 사용한다. 현재 컨테이너 설치 버전이나 공급망 검증 결과는 아니다. frontend와 AI 의존성은 각 package-lock.json/requirements.txt가 별도 근거다.

| 패키지 | 버전 | 추적 파일 수 | package.json의 설명 |
| --- | --- | ---: | --- |
| accepts | 1.3.8 | 5 | Higher-level content negotiation |
| array-flatten | 1.1.1 | 4 | Flatten an array of nested arrays into a single flat array |
| body-parser | 1.20.8 | 10 | Node.js body parsing middleware |
| bytes | 3.1.2 | 5 | Utility to parse a string bytes to bytes and vice-versa |
| call-bind-apply-helpers | 1.0.2 | 21 | Helper functions around Function call/apply/bind, for use in `call-bind` |
| call-bound | 1.0.4 | 11 | Robust call-bound JavaScript intrinsics, using `call-bind` and `get-intrinsic`. |
| content-disposition | 0.5.4 | 5 | Create and parse Content-Disposition header |
| content-type | 1.0.5 | 5 | Create and parse HTTP Content-Type header |
| cookie | 0.7.2 | 5 | HTTP server cookie parsing and serialization |
| cookie-signature | 1.0.7 | 4 | Sign and unsign cookies |
| debug | 2.6.9 | 17 | small debugging utility |
| depd | 2.0.0 | 6 | Deprecate all the things |
| destroy | 1.2.0 | 4 | destroy a stream if possible |
| dunder-proto | 1.0.1 | 15 | If available, the `Object.prototype.__proto__` accessor and mutator, call-bound |
| ee-first | 1.1.1 | 4 | return the first event in a set of ee/event pairs |
| encodeurl | 2.0.0 | 4 | Encode a URL to a percent-encoded form, excluding already-encoded sequences |
| es-define-property | 1.0.1 | 11 | `Object.defineProperty`, but not IE 8's broken one. |
| es-errors | 1.3.0 | 22 | A simple cache for a few of the JS Error constructors. |
| es-object-atoms | 1.1.2 | 16 | ES Object-related atoms: Object, ToObject, RequireObjectCoercible |
| escape-html | 1.0.3 | 4 | Escape string for use in HTML |
| etag | 1.8.1 | 5 | Create simple HTTP ETags |
| express | 4.22.3 | 16 | Fast, unopinionated, minimalist web framework |
| finalhandler | 1.3.2 | 6 | Node.js final http responder |
| forwarded | 0.2.0 | 5 | Parse HTTP X-Forwarded-For header |
| fresh | 0.5.2 | 5 | HTTP response freshness testing |
| function-bind | 1.1.2 | 12 | Implementation of Function.prototype.bind |
| get-intrinsic | 1.3.0 | 9 | Get and robustly cache all JS language-level intrinsics at first require time |
| get-proto | 1.0.1 | 15 | Robustly get the [[Prototype]] of an object |
| gopd | 1.2.0 | 12 | `Object.getOwnPropertyDescriptor`, but accounts for IE's broken implementation. |
| has-symbols | 1.1.0 | 16 | Determine if the JS environment has Symbol support. Supports spec, or shams. |
| hasown | 2.0.4 | 10 | A robust, ES3 compatible, "has own property" predicate. |
| http-errors | 2.0.1 | 5 | Create HTTP error objects |
| iconv-lite | 0.4.24 | 26 | Convert character encodings in pure javascript. |
| inherits | 2.0.4 | 5 | Browser-friendly inheritance fully compatible with standard node.js inherits() |
| ipaddr.js | 1.9.1 | 6 | A library for manipulating IPv4 and IPv6 addresses in JavaScript. |
| math-intrinsics | 1.1.0 | 38 | ES Math-related intrinsics and helpers, robustly cached. |
| media-typer | 0.3.0 | 5 | Simple RFC 6838 media type parser and formatter |
| merge-descriptors | 1.0.3 | 5 | Merge objects using descriptors |
| methods | 1.1.2 | 5 | HTTP methods that node supports |
| mime | 1.6.0 | 10 | A comprehensive library for mime-type mapping |
| mime-db | 1.52.0 | 6 | Media Type Database |
| mime-types | 2.1.35 | 5 | The ultimate javascript content-type utility. |
| ms | 2.0.0 | 4 | Tiny milisecond conversion utility |
| negotiator | 0.6.3 | 9 | HTTP content negotiation |
| object-inspect | 1.13.4 | 34 | string representations of objects in node and the browser |
| on-finished | 2.4.1 | 5 | Execute a callback when a request closes, finishes, or errors |
| parseurl | 1.3.3 | 5 | parse a url with memoization |
| path-to-regexp | 0.1.13 | 4 | Express style path to RegExp utility |
| playwright | 1.63.0 | 62 | A high-level API to automate web browsers |
| playwright-core | 1.63.0 | 114 | A high-level API to automate web browsers |
| proxy-addr | 2.0.8 | 5 | Determine address of proxied request |
| qs | 6.16.0 | 20 | A querystring parser that supports nesting and arrays, with a depth limit |
| range-parser | 1.2.1 | 5 | Range header field string parser |
| raw-body | 2.5.3 | 5 | Get and validate the raw body of a readable stream. |
| safe-buffer | 5.2.1 | 5 | Safer Node.js Buffer API |
| safer-buffer | 2.1.2 | 7 | Modern Buffer API polyfill without footguns |
| send | 0.19.2 | 10 | Better streaming static file server with Range and conditional-GET support |
| serve-static | 1.16.3 | 5 | Serve static files |
| setprototypeof | 1.2.0 | 6 | A small polyfill for Object.setprototypeof |
| side-channel | 1.1.1 | 12 | Store information about any JS value in a side channel. Uses WeakMap if available. |
| side-channel-list | 1.0.1 | 13 | Store information about any JS value in a side channel, using a linked list |
| side-channel-map | 1.0.1 | 12 | Store information about any JS value in a side channel, using a Map |
| side-channel-weakmap | 1.0.2 | 12 | Store information about any JS value in a side channel. Uses WeakMap if available. |
| statuses | 2.0.2 | 6 | HTTP status utility |
| toidentifier | 1.0.1 | 5 | Convert a string of words to a JavaScript identifier |
| type-is | 1.6.18 | 5 | Infer the content-type of a request. |
| unpipe | 1.0.0 | 5 | Unpipe a stream from all destinations |
| utils-merge | 1.0.1 | 5 | merge() utility function |
| vary | 1.1.2 | 5 | Manipulate the HTTP Vary header |
