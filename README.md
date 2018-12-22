# clj-kraken

talk with kraken api from clojure

## Usage

```
[org.clojars.kokos/clj-kraken "0.1.4"]
```

```clojure

(ns example
    (:require [clj-kraken.core :as kraken]))

(def conf {:api {:version "0"
                :host "api.kraken.com"
                :scheme "https://"
                :key "...."
                :secret "...."}})

(kraken/assets conf {})

(kraken/balance conf {})


```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
