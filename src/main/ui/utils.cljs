(ns ui.utils
  (:require
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [cljs-http.client :as http]
    [applied-science.js-interop :as j]
    [clojure.string :as str]))


(defn log
  [& args]  (apply js/console.log args))

;; ---- Datascript specific ------

(defn q
  ([query]
   (let [serialised-query (if (string? query)
                            query
                            (pr-str query))
         roam-api         (.-data (.-roamAlphaAPI js/window))
         q-fn             (.-q roam-api)]
     (-> (.apply q-fn roam-api (array serialised-query))
       (js->clj :keywordize-keys true))))
  ([query & args]
   (let [serialised-query (if (string? query)
                            query
                            (pr-str query))
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

(defn get-block-parent-with-order [block-uid]
  (first (q '[:find ?pu ?o
              :in $ ?uid
              :where [?e :block/uid ?uid]
              [?e :block/parents ?p]
              [?p :block/uid ?pu]
              [?e :block/order ?o]]
           block-uid)))


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


(defn create-new-block-with-id [{:keys [parent-uid block-uid order string callback open]}]
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    block-uid
                             :string string
                             :open   open}}))
    (.then (fn []
             callback))))

(defn get-todays-uid []
  (->> (js/Date.)
    (j/call-in js/window [:roamAlphaAPI :util :dateToPageUid])))
(get-todays-uid)

(defn gen-new-uid []
  (j/call-in js/window [:roamAlphaAPI :util :generateUID]))

(defn get-open-page-uid []
  (j/call-in js/window [:roamAlphaAPI :ui :mainWindow :getOpenPageOrBlockUid]))

(get-open-page-uid)

;; The keys s - string, c - children, u - uid, op - open, o - order
#_(extract-struct
    {:s "AI chats"
     :c [{:s "{{ chat-llm }}"
          :c [{:s "Messages"}
              {:s "Context"}]}]}
    "8yCGreTXI")

(defn create-struct [struct top-parent chat-block-uid open-in-sidebar?]
  (let [stack (atom [struct])
        res   (atom [top-parent])]
    (go
      (while (not-empty @stack)
         (let [cur (first @stack)
               {:keys [u s o op]} cur
               new-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])
               parent (first @res)
               args {:parent-uid parent
                     :block-uid  (if (some? u) u new-uid)
                     :order      (if (some? o) o "last")
                     :string     s
                     :open      (if (some? op) op true)}]

             #_(println "args" args)
             (swap! stack rest)
             (swap! stack #(vec (concat % (:c cur))))
             ;(println "block-" string "-parent-" parent #_(first @res))
             (<p! (create-new-block-with-id args))
             ;(cljs.pprint/pprint  args)
             (swap! res rest)
             (swap! res #(vec (concat % (vec (repeat (count (:c cur))
                                               (if (some? (:u cur))
                                                 (:u cur)
                                                 new-uid))))))))
      (when open-in-sidebar?
        (<p! (-> (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :addWindow]
                   (clj->js {:window {:type "block"
                                      :block-uid chat-block-uid
                                      :order 0}}))
               (.then (fn []
                         (do
                          #_(println "window added to right sidebar")
                          (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :open]))))))))))

(defn get-focused-block []
  (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
    (j/get :block-uid)))

(get-block-parent-with-order "khffC8IRS")


(defn default-chat-struct
  ([]
   (default-chat-struct nil nil nil))
  ([chat-block-uid]
   (default-chat-struct chat-block-uid nil nil))
  ([chat-block-uid context-block-uid]
   (default-chat-struct chat-block-uid context-block-uid nil))
  ([chat-block-uid context-block-uid chat-block-order]
   {:s "{{ chat-llm }}"
    :op false
    :o chat-block-order
    :u (or chat-block-uid nil)
    :c [{:s "Messages"}
        {:s "Context"
         :c [{:s ""}]
         :u (or nil context-block-uid)}]}))

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





