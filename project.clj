(defproject net.clojars.sorrop/easy-nio "1.0.0"
  :description "A collection of clojure wrappers around java.nio facilities"
  :url "https://github.com/Sorrop/easy-nio"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.2"]]

  :repl-options {:init-ns user}

  :repositories [["releases" {:url   "https://repo.clojars.org"
                              :creds :gpg}]]

  :plugins [[lein-codox "0.10.8"]
            [lein-set-version "0.4.1"]]

  :codox {:output-path "docs"
          :source-uri  "https://github.com/Sorrop/easy-nio/blob/master/{filepath}#L{line}"
          :namespaces  [easy-nio.buffer
                        easy-nio.channel
                        easy-nio.selector
                        easy-nio.file
                        easy-nio.protocols]
          :metadata    {:doc/format :markdown}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["codox"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
