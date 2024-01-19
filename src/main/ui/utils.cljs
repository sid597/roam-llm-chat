(ns ui.utils
  (:require
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [cljs-http.client :as http]
    [applied-science.js-interop :as j]
    [clojure.string :as str]))


(defn log
  [& args]  (apply js/console.log args))

;; ---- Datascript specific ------

(defn q
  ([query]
   (let [serialised-query (pr-str query)
         roam-api         (.-data (.-roamAlphaAPI js/window))
         q-fn             (.-q roam-api)]
     (-> (.apply q-fn roam-api (array serialised-query))
       (js->clj :keywordize-keys true))))
  ([query & args]
   (let [serialised-query (pr-str query)
         roam-api         (.-data (.-roamAlphaAPI js/window))
         q-fn             (.-q roam-api)]
     (-> (.apply q-fn roam-api (apply array (concat [serialised-query] args)))
       (js->clj :keywordize-keys true)))))


(defn uid-to-block [uid]
  (q '[:find (pull ?e [*])
       :in $ ?uid
       :where [?e :block/uid ?uid]]
    uid))


(defn get-eid [title]
  (ffirst (q '[:find ?eid
               :in $ ?title
               :where [?eid :node/title ?title]]
            title)))

(defn is-a-page? [s]
  (second (re-find #"\[\[(.+)\]\]" s)))

(defn extract-from-code-block [s]
  (let [pattern #"(?s)```javascript\n \n(.*?)\n```"
        m (re-find pattern s)]
    (if m
      (str (second m) " \n ")
      (str s " \n "))))

(defn get-child-with-str [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid :block/order {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))

(defn get-parent-parent [uid]
  (ffirst (q '[:find  ?p
               :in $ ?uid
               :where [?e :block/uid ?uid]
               [?e :block/parents ?p1]
               [?p1 :block/string "Context"]
               [?p1 :block/parents ?p2]
               [?p2 :block/string "{{ chat-llm }}"]
               [?p2 :block/uid ?p]]
            uid)))

;; --- Roam specific ---

(defn remove-entry [block-str]
  (let [patterns ["Entry:SmartBlock:"
                  "\\{\\{Create Today's Entry:SmartBlock"
                  "\\{\\{Pick A Day:SmartBlock"
                  "\\#.sticky"]
        regex-pattern (str/join "|"
                        (map #(str % ".*?(\\s|$)") patterns))]
    (str/replace block-str (re-pattern regex-pattern) "")))

(defn replace-block-uids [block-str]
  (let [re (re-pattern "\\(\\(\\(?([^)]*)\\)?\\)\\)")
        matches (re-seq re block-str)]
    (-> (reduce (fn [s [whole-match uid]]

                  (let [replace-str (str
                                      (:string (ffirst (uid-to-block uid))))

                        replacement (if (str/starts-with? whole-match "(((")
                                      (str "(" replace-str ")")
                                      replace-str)]
                    (str/replace s whole-match replacement)))
          block-str
          matches)
      remove-entry)))

(defn move-block [parent-uid order block-uid]
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :move]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    block-uid}}))
    (.then (fn []
             #_(println "new block in messages")))))


(defn create-new-block [parent-uid order string callback]
  #_(println "create new block" parent-uid)
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                             :string string
                             :open   true}}))
    (.then (fn []
             callback))))

(defn update-block-string-and-move [block-uid string parent-uid order]
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :update]
        (clj->js {:block {:uid    block-uid
                          :string string}}))
    (.then (fn []
             #_(println "-- updated block string now moving --")
             (move-block parent-uid order block-uid)))))

;; ---- Open ai specific ----

(goog-define url-endpoint "")

(defn call-openai-api [{:keys [messages settings callback]}]
  (let [passphrase (j/get-in js/window [:localStorage :passphrase])
        url     "https://roam-llm-chat-falling-haze-86.fly.dev/chat-complete"
        data    (clj->js {:documents messages
                          :settings settings
                          :passphrase passphrase})
        headers {"Content-Type" "application/json"}
        res-ch  (http/post url {:with-credentials? false
                                :headers headers
                                :json-params data})]
    (take! res-ch callback)))
