(ns ui.utils
  (:require
    ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [cljs.reader :as reader]
    [cljs-http.client :as http]
    [applied-science.js-interop :as j]
    [clojure.string :as str]))


(defn log
  [& args]  (apply js/console.log args))

(defn p [& args]
  (apply println (str "CL: ") args))


(def markdown-image-pattern #"!\[([^\]]*)\]\((.*?)\)")
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

(defn uid->title [uid]
  (ffirst (q '[:find ?title
               :in $ ?uid
               :where
               [?e :block/uid ?uid]
               [?e :node/title ?title]]

            uid)))

(defn title->uid [title]
  (ffirst (q '[:find ?uid
               :in $ ?title
               :where
               [?e :node/title ?title]
               [?e :block/uid ?uid]]
            title)))


(defn get-eid [title]
  (ffirst (q '[:find ?eid
               :in $ ?title
               :where [?eid :node/title ?title]]
            title)))

(defn uid->eid [uid]
  (ffirst (q '[:find ?eid
               :in $ ?uid
               :where [?eid :block/uid ?uid]]
            uid)))

(defn is-a-page? [s]
  (second (re-find #"^\[\[(.*?)\]\]$" (clojure.string/trim s))))

(comment
  (is-a-page? " [[[[ISS]] - scope other options for the LLM chat interface]]")
  (is-a-page? "[[EVD]] - NWASP was found around clusters of clathrin heavy chain by TIRF microscopy + super resolution microscopy - [[@leyton-puig2017flat]]"))


(defn extract-from-code-block [s]
  (let [pattern #"(?s)```javascript\n \n(.*?)\n```"
        m (re-find pattern s)]
    (if m
      (str (second m) " \n ")
      (str s " \n "))))


(defn unnamespace-keys
  [m]
  (let [remove-ns (fn [k] (keyword (name k)))] ; Helper to remove namespace
    (into {}
      (mapv (fn [[k v]]
              [(remove-ns k) ; Remove namespace from key
               (if (map? v)
                 (unnamespace-keys v) ; Recursively process nested maps
                 (if (coll? v)
                   (mapv unnamespace-keys v) ; Apply recursively to each item if v is a collection
                   v))]) ; Leave other values unchanged
        m))))

(defn add-pull-watch
  [pull-pattern entity-id callback]
  (let [roam-api (.-data (.-roamAlphaAPI js/window))
        add-pull-watch-fn (.-addPullWatch roam-api)
        js-callback (fn [before after]
                      (let [b (unnamespace-keys (js->clj before :keywordize-keys true))
                            a (unnamespace-keys (js->clj after :keywordize-keys true))]
                        (callback b a)))]
    (.addPullWatch roam-api pull-pattern entity-id js-callback)))



(defn watch-children [block-uid cb]
  (let [pull-pattern "[:block/uid :block/order {:block/children ...}]"
        entity-id (str [:block/uid block-uid])]
    (println "add pull watch :" entity-id)
    (add-pull-watch pull-pattern entity-id cb)))

(defn watch-string [block-uid cb]
  (let [pull-pattern "[:block/uid :block/string]"
        entity-id (str [:block/uid block-uid])]
    (p "add string watch :" entity-id)
    (add-pull-watch pull-pattern entity-id cb)))

(defn get-child-with-str  [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid :block/order {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))

(defn get-child-of-child-with-str
  ([block-uid p1 p2]
   (get-child-of-child-with-str block-uid p1 p2 true))
  ([block-uid p1 p2 only-string?]
   (let [res (ffirst (q '[:find  (pull ?c3 [:block/string :block/uid])
                          :in $ ?uid ?p1 ?p2
                          :where
                          [?e :block/uid ?uid]
                          [?e :block/children ?c1]
                          [?c1 :block/string ?p1]
                          [?c1 :block/children ?c2]
                          [?c2 :block/string ?p2]
                          [?c2 :block/children ?c3]
                          [?c3 :block/string ?p3]]
                       block-uid
                       p1
                       p2))]
      (if only-string?
        (:string res)
        (:uid res)))))


(defn get-child-of-child-with-str-on-page [page p1 p2 p3]
  (ffirst (q '[:find  (pull ?c3 [:block/string :block/uid :block/order {:block/children ...}])
               :in $ ?page ?p1 ?p2 ?p3
               :where
               [?e :node/title ?page]
               [?e :block/children ?c1]
               [?c1 :block/string ?p1]
               [?c1 :block/children ?c2]
               [?c2 :block/string ?p2]
               [?c2 :block/children ?c3]
               [?c3 :block/string ?p3]]
            page
            p1
            p2
            p3)))



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

(defn block-with-str-on-page? [page bstr]
  (ffirst
    (q '[:find ?uid
         :in $ ?today ?bstr
         :where
         [?e :block/uid ?today]
         [?e :block/children ?c]
         [?c :block/string ?bstr]
         [?c :block/uid ?uid]]
      page
      bstr)))


(defn ai-block-exists? [page]
  (block-with-str-on-page? page "AI chats"))


;; --- Roam specific ---

(defn get-todays-uid []
  (->> (js/Date.)
    (j/call-in js/window [:roamAlphaAPI :util :dateToPageUid])))
(get-todays-uid)

(defn gen-new-uid []
  (j/call-in js/window [:roamAlphaAPI :util :generateUID]))

(defn get-open-page-uid []
  (j/call-in js/window [:roamAlphaAPI :ui :mainWindow :getOpenPageOrBlockUid]))

(comment
  (get-open-page-uid))

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

(comment
  (re-seq (re-pattern "\\(\\(\\(?([^)]*)\\)?\\)\\)") "((hello))"))

(defn extract-embeds [block-str]
  (let [uid (second (first (re-seq
                             (re-pattern "\\{\\{\\[\\[embed\\]\\]\\: \\(\\(\\(?([^)]*)\\)?\\)\\)")
                             block-str)))
        c  (when (some? uid)
             (q '[:find (pull ?e [:block/string {:block/children ...}])
                  :in $ ?uid
                  :where
                  [?e :block/uid ?uid]]
               uid))]
    c))


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
                  :block    {:uid    (gen-new-uid)
                             :string string
                             :open   true}}))
    (.then (fn []
             callback))))

(defn update-block-string
  ([block-uid string]
   (update-block-string block-uid string #()))
  ([block-uid string callback]
   (-> (j/call-in js/window [:roamAlphaAPI :data :block :update]
         (clj->js {:block {:uid    block-uid
                           :string string}}))
     (.then (fn [] callback)))))

(defn update-block-string-for-block-with-child [block-uid c1 c2 new-string]
  (let [uid (get-child-of-child-with-str block-uid c1 c2 false)]
    (update-block-string uid new-string)))


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

(defn create-new-page
  ([title]
   (let [uid (gen-new-uid)]
     (create-new-page title uid)))
  ([title uid]
   (-> (j/call-in js/window [:roamAlphaAPI :data :page :create]
         (clj->js {:page {:title title
                          :uid   uid}}))
     (.then (fn []
              #_(println "new page created"))))))

;; The keys s - string, c - children, u - uid, op - open, o - order
#_(extract-struct
    {:s "AI chats"
     :c [{:s "{{ chat-llm }}"
          :c [{:s "Messages"}
              {:s "Context"}]}]}
    "8yCGreTXI")

(defn create-struct
  ([struct top-parent chat-block-uid open-in-sidebar?]
   (create-struct struct top-parent chat-block-uid open-in-sidebar? #()))
  ([struct top-parent chat-block-uid open-in-sidebar? cb]
   (let [pre   "*Creating struct*: "
         stack (atom [struct])
         t? (:t struct)
         res (atom [top-parent])]
     (p pre struct)
     (p (str pre "open in sidebar?") open-in-sidebar?)
     (go
       (while (not-empty @stack)
          (let [cur                  (first @stack)
                {:keys [t u s o op]} cur
                new-uid              (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                parent               (first @res)
                args                 {:parent-uid parent
                                       :block-uid  (if (some? u) u new-uid)
                                       :order      (if (some? o) o "last")
                                       :string     s
                                       :open      (if (some? op) op true)}]
              (swap! stack rest)
              (swap! stack #(vec (concat % (:c cur))))
              ;(println "block-" string "-parent-" parent #_(first @res))
              (p (str pre "creating with args: " t  " -- " args))
              (if (some? t)
                (<p! (create-new-page t (if (some? u) u new-uid)))
                (<p! (create-new-block-with-id args)))
              (swap! res rest)
              (swap! res #(vec (concat % (vec (repeat (count (:c cur))
                                                (if (some? (:u cur))
                                                  (:u cur)
                                                  new-uid))))))))
       (when open-in-sidebar?
         (p "open window in sidebar? " open-in-sidebar?)
         (<p! (-> (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :addWindow]
                    (clj->js {:window {:type "block"
                                       :block-uid chat-block-uid
                                       :order 0}}))
                (.then (fn []
                          (do
                           (p "Window added to right sidebar")
                           (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :open])))))))

      (<p! (js/Promise. (fn [_] cb)))))))


(defn get-focused-block []
  (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
    (j/get :block-uid)))

(get-block-parent-with-order "khffC8IRS")

(defn llm-chat-settings-page-struct []
  (let [page-uid    (gen-new-uid)
        page-title  "LLM chat settings"
        page-exist? (some? (get-eid page-title))]
    (p "*" page-title "* page exists?" page-exist?)
    (when (not page-exist?)
      (p "*" page-title "* page does NOT exist, so creating it")
      (create-struct
        {:t  page-title
         :u page-uid
         :c [{:s "Quick action buttons"
              :c [{:s "Summarise this page"
                   :c [{:s "Context"
                        :c [{:s "This is Dr. Akamatsu's biology lab at the University of Washington. Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.\n\nWe capture questions (QUE), hypotheses (HYP), and conclusions (CON) on separate pages in Roam. Each page has a title summarizing the key insight, a body elaborating on findings and literature, and hierarchical references (refs) linking to related pages. The refs show the lineage of ideas from one page to detailed explorations on another.\n\nFor example, a QUE page may ask \"How does the Arp2/3 complex bind to actin filaments?\" This could link to a HYP page proposing a molecular binding mechanism as a hypothesis. The HYP page would in turn link to CON pages concluding whether our hypothesis was supported or refuted.\n\nOur pages integrate knowledge from publications, data visualizations, and discussions with experts in the field. By connecting the dots across pages, we maintain an audit trail of the evolution in our research.\n\nThe provided page data reflects this structure, each individual page is a map with keys `:title`, `:body` and `:refs`. The body content combines biology expertise with our lab's own analyses and experimental data.\n\nGiven the following data from a page summarise it for me as  nexpert in this field. Use the linked references for your answers, go in depth.\n\nData from page: "}]}]}]}
             {:s "Settings"
              :c [{:s "Token count"
                   :c [{:s "0"}]}
                  {:s "Model"
                   :c [{:s "gpt-3.5"}]}
                  {:s "Max tokens"
                   :c [{:s "400"}]}
                  {:s "Temperature"
                   :c [{:s "0.9"}]}
                  {:s "Get linked refs"
                   :c [{:s "false"}]}]}
             {:s "Graph overview default pre prompt"
              :c [{:s "Pre prompt:"
                   :c [{:s "This is Dr. Akamatsu's biology lab at the University of Washington. Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.\n\nWe capture questions (QUE), hypotheses (HYP), and conclusions (CON) on separate pages in Roam. Each page has a title summarizing the key insight, a body elaborating on findings and literature, and hierarchical references (refs) linking to related pages. The refs show the lineage of ideas from one page to detailed explorations on another.\n\nFor example, a QUE page may ask \"How does the Arp2/3 complex bind to actin filaments?\" This could link to a HYP page proposing a molecular binding mechanism as a hypothesis. The HYP page would in turn link to CON pages concluding whether our hypothesis was supported or refuted.\n\nOur pages integrate knowledge from publications, data visualizations, and discussions with experts in the field. By connecting the dots across pages, we maintain an audit trail of the evolution in our research.\n\nThe provided page data reflects this structure, each individual page is a map with keys `:title`, `:body` and `:refs`. The body content combines biology expertise with our lab's own analyses and experimental data."}]}]}
             {:s "Image prompt"
              :c [{:s "Default prompt"
                   :c [{:s "What's in this image?"}]}
                  {:s "Settings"
                   :c [{:s "Max tokens"
                        :c [{:s "300"}]}
                       {:s "Generate description for:"
                        :c [{:s "Images without description"}]}]}]}]}


        page-uid
        nil
        nil
        (p "*" page-title "* page Created")))))



(def settings-struct
  {:s "Settings"
   :op false
   :c [{:s "Token count"
        :c [{:s "0"}]}
       {:s "Model"
        :c [{:s "gpt-3.5"}]}
       {:s "Max tokens"
        :c [{:s "400"}]}
       {:s "Temperature"
        :c [{:s "0.9"}]}
       {:s "Get linked refs"
        :c [{:s "true"}]}
       {:s "Active?"
        :c [{:s "false"}]}]})


(defn common-chat-struct [context-structure context-block-uid context-open?]
  [{:s "Messages"}
   {:s "Context"
    :op (or context-open? true)
    :c (or context-structure [{:s ""}])
    :u (or context-block-uid nil)}
   {:s "Chat"
    :c [{:s ""}]}
   settings-struct])


(defn default-chat-struct
  ([]
   (default-chat-struct nil nil nil))
  ([chat-block-uid]
   (default-chat-struct chat-block-uid nil nil))
  ([chat-block-uid context-block-uid]
   (default-chat-struct chat-block-uid context-block-uid nil))
  ([chat-block-uid context-block-uid chat-block-order]
   (default-chat-struct chat-block-uid context-block-uid chat-block-order nil))
  ([chat-block-uid context-block-uid chat-block-order context-structure]
   {:s "{{ chat-llm }}"
    :op false
    :o chat-block-order
    :u (or chat-block-uid nil)
    :c (common-chat-struct context-structure context-block-uid false)}))


(defn chat-ui-with-context-struct
  ([]
   (chat-ui-with-context-struct nil nil nil))
  ([chat-block-uid]
   (chat-ui-with-context-struct chat-block-uid nil nil))
  ([chat-block-uid context-block-uid]
   (chat-ui-with-context-struct chat-block-uid context-block-uid nil))
  ([chat-block-uid context-block-uid  context-structure]
   (chat-ui-with-context-struct chat-block-uid context-block-uid context-structure nil))
  ([chat-block-uid context-block-uid  context-structure chat-block-order]
   {:s "AI chats"
    :o chat-block-order
    :c [{:s "{{ chat-llm }}"
         :op false
         :u (or chat-block-uid nil)
         :c (common-chat-struct context-structure context-block-uid false)}]}))


;; ---- ai specific ----

(def model-mappings
  {"gpt-4"            "gpt-4-0125-preview"
   "gpt-3.5"          "gpt-3.5-turbo-0125"
   "claude-3-sonnet"  "claude-3-sonnet-20240229"
   "claude-3-opus"    "claude-3-opus-20240229"})

(defn model-type [model-name]
  (cond
    (str/starts-with? model-name "gpt")          :gpt
    (str/starts-with? model-name "claude")       :claude
    :else                                        :unknown))


(goog-define url-endpoint "")

(defn call-api [url messages settings callback]
  (p (str "------------ " url))
  (p (str "------------ " settings))
  (let [passphrase (j/get-in js/window [:localStorage :passphrase])
        data    (clj->js {:documents messages
                          :settings settings
                          :passphrase passphrase})
        headers {"Content-Type" "application/json"}
        res-ch  (http/post url {:with-credentials? false
                                :headers headers
                                :json-params data})]
    (take! res-ch callback)))

(defn call-llm-api [{:keys [messages settings callback]}]
  (let [model (model-type (:model settings))]
    (case model
      :gpt        (call-api "http://localhost:3000/chat-complete" ; "https://roam-llm-chat-falling-haze-86.fly.dev/chat-complete"
                    messages settings callback)
      :claude     (call-api "http://localhost:3000/chat-anthropic" ;"https://roam-llm-chat-falling-haze-86.fly.dev/chat-anthropic"
                    messages settings callback)
      (p "Unknown model"))))


(defn count-tokens-api [{:keys [model message token-count-atom update? block-uid]}]
  (p "Count tokens api called")
  (let [url    "https://roam-llm-chat-falling-haze-86.fly.dev/count-tokens"
        data    (clj->js {:model "gpt-4"
                          :message message})
        headers {"Content-Type" "application/json"}
        res-ch  (http/post url {:with-credentials? false
                                :headers headers
                                :json-params data})]
    (take! res-ch (fn [res]
                    (let [count (-> res
                                   :body)
                          new-count (if update?
                                      (+ (js/parseInt  @token-count-atom)
                                         (js/parseInt count))
                                      count)]
                      (p "*Old Token count* :" @token-count-atom)
                      (update-block-string-for-block-with-child block-uid "Settings" "Token count" (str new-count))
                      (reset! token-count-atom new-count)
                      (p "*New Token count* :" new-count))))))


(defn create-alternate-messages [messages initial-context pre]
  (let [current-message   (atom (str (extract-from-code-block initial-context)))
        alternate-messages (atom [])]
    (p (str pre "create alternate messages"))
    (doseq [msg messages]
      (let [msg-str (:string msg)]
        (if (str/starts-with? msg-str "**Assistant:** ")
          (do
            (swap! alternate-messages conj {:role    "user"
                                            :content (str @current-message)})
            (swap! alternate-messages conj {:role    "assistant"
                                            :content (str (clojure.string/replace msg-str #"^\*\*Assistant:\*\* " ""))})
            (reset! current-message ""))
          (swap! current-message #(str % "\n" (extract-from-code-block msg-str))))))
    (when (not-empty @current-message)
      (swap! alternate-messages conj {:role    "user"
                                      :content (str @current-message)}))
    @alternate-messages))



(defn image-to-text-for [nodes total-images-atom loading-atom image-prompt-str max-tokens]
  (doseq [node nodes]
    (let [url      (-> node :match :url)
          uid      (-> node :uid)
          text     (-> node :match :text)
          block-string (-> node :string)
          messages [{:role "user"
                     :content [{:type "text"
                                :text (str image-prompt-str)}
                               {:type "image_url"
                                :image_url {:url url}}]}]
          settings  {:model       "gpt-4-vision-preview"
                     :max-tokens  @max-tokens
                     :temperature 0.9}
          callback (fn [response]
                      (let [res-str          (-> response
                                               :body)
                            new-url          (str "![" res-str "](" url ")")
                            new-block-string (str/replace block-string markdown-image-pattern new-url)
                            new-count        (dec @total-images-atom)]
                        (p "image to text for uid" uid)
                        (p "Old image description: " text)
                        (p "old block string" block-string)
                        (p "Image to text response received, new image description: " res-str)
                        (p "New block string" new-block-string)
                        (p "Images to describe count: " @total-images-atom)
                        (if (zero? new-count)
                          (do
                            (p "All images described")
                            (reset! loading-atom false))
                          (swap! total-images-atom dec))
                        (update-block-string
                          uid
                          new-block-string)))]
      (p "Messages for image to text" messages)
      (call-llm-api
        {:messages messages
         :settings settings
         :callback callback}))))


;; ---- UI ---


(defn inject-style []
  (let [style-element (.createElement js/document "style")
        css-string ".sp svg { color: cadetblue; }"] ; Change 'blue' to your desired color

    (set! (.-type style-element) "text/css")
    (when (.-styleSheet style-element) ; For IE8 and below.
      (set! (.-cssText (.-styleSheet style-element)) css-string))
    (when-not (.-styleSheet style-element) ; For modern browsers.
      (let [text-node (.createTextNode js/document css-string)]
        (.appendChild style-element text-node)))
    (.appendChild (.-head js/document) style-element)))


(defn send-message-component [active? callback]
  (inject-style)
  [:> Button {:class-name "sp"
              :style {:width "30px"}
              :icon (if @active? nil "send-message")
              :minimal true
              :fill false
              :small true
              :loading @active?
              :on-click #(do #_(println "clicked send message compt")
                           (callback {}))}])

(defn button-popover
  ([button-text render-comp]
   (button-popover button-text render-comp "#eeebeb"))
  ([button-text render-comp bg-color]
   [:> Popover
    {:position "bottom"}
    [:> Button {:minimal true
                :small true}
     button-text]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))

(defn settings-button-popover
  ([render-comp]
   (settings-button-popover render-comp "#eeebeb"))
  ([render-comp bg-color]
   [:> Popover
    ;{:position "bottom"}
    [:> Button {:icon "cog"
                :minimal true
                :small true
                :style {:background-color bg-color}}]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))
