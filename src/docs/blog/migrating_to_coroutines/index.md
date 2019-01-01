Migration task list
===============

- Migrate Servers to async model:
     - ApacheServer [ ]
     - Jetty [ ]
     - Netty [ ]
     - ~~SunHttp~~
     - Undertow [ ]
     
- Migrate Clients to async model:
    - Apache - Reimplement in terms of Async API  - drop Async module all together [ ]
    - ~~Java~~
    - Jetty - Reimplement in terms of Async API - bridge to coroutines [ ]
    - OkHttp - Reimplement in terms of Async API - bridge to coroutines [ ]

- Replace ThreadLocals with CoroutineContext
    - ZipkinTraces
    - RequestContexts?

- Websockets ???
   
- Pre-release version:
    1. Release with 0.0.0.X version
    1. New versions of examples - use a new v4 branch
    1. Add implementation to TFB - switch to using non-blocking database

- Blog post introducing changes to API