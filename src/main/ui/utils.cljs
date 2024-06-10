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


(defn extract-all-discourse-for [node]
  (let [node-regex (re-pattern (cond
                                 (= "EVD" node) (str "^\\[\\[EVD\\]\\] - (.*?) - (.*?)$")
                                 :else (str "^\\[\\[" node "\\]\\] - (.*?)$")))]
    (flatten (q '[:find (pull ?e [:node/title :block/uid])
                  :in $ ?node-regex
                  :where
                  [?e :node/title ?tit]
                  [(re-find ?node-regex ?tit)]]
               node-regex))))


(defn extract-all-sources []
  (let [node-regex (re-pattern "^@[^\\s]+$")]
    (into [] (flatten (q '[:find
                           (pull ?node [:block/string :node/title :block/uid])
                           :where
                           [(re-pattern "^@(.*?)$") ?.*?$-regex]
                           [?node :node/title ?node-Title]
                           [(re-find ?.*?$-regex ?node-Title)]])))))


(defn all-dg-nodes []
 (let [cnt (atom [])
       nodes ["QUE" "CLM" "EVD" "RES" "ISS" "HYP" "CON"]]
   (doseq [node nodes]
     (let [total  (extract-all-discourse-for node)]
       (swap! cnt concat total)))
   (into [] (concat (into [] @cnt) (extract-all-sources)))))
(count (all-dg-nodes))

(def all-title
  (q '[:find ?u  ?t
       :where [?e :node/title ?t]
       [?e :block/uid ?u]]))

(def dg-nodes
  (filter
    #(let [uid (str (first %))
           dg? (j/call-in js/window [:roamjs :extension :queryBuilder :isDiscourseNode] uid)]
       (when dg?
         %))
    all-title))

(defn node-count [t]
  (->> (filter
         #(let [s (second %)]
            (when (clojure.string/starts-with? s (str "[[" t "]]"))
              (second %)))
         dg-nodes)
    (map second)))


(comment
  (count (node-count "EVD"))
  (node-count "EVD"))

(comment
  (extract-all-discourse-for "EVD")
  (into #{} (node-count "EVD"))
  (clojure.set/difference
     (into #{} (->> (extract-all-discourse-for "EVD")
                 (map :title)))
     (into #{} (node-count "EVD")))
 (count (extract-all-discourse-for "CLM"))
 (count (extract-all-discourse-for "EVD"))
 (count (extract-all-discourse-for "HYP"))
 (count (extract-all-discourse-for "RES"))
 (count (extract-all-discourse-for "ISS"))
 (count (extract-all-discourse-for "QUE"))
 (count (extract-all-discourse-for "EXP"))
 (count (extract-all-discourse-for "CON"))
 (count (q '[:find ?t
             :where [?e :node/title]])))



(comment
  (flatten extract-all-sources))

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
        pres? (re-find pattern s)
        relaxed-pattern #"(?s)```\s*(.*?)\s*```"
        rpres? (re-find relaxed-pattern s)]
    (cond
      pres?  (str (second pres?) " \n ")
      rpres? (str (clojure.string/trim (second rpres?)) " \n ")
      :else  (str s " \n "))))

(comment
  (def t "Here is some code:\n```\nprint('Hello, world!')\n```")
  (def tt "```javascript\n \nconsole.log('Hello, world!');\n```")
  (def n "```
        [[CLM]] - Actin accumulation at sites of endocytosis increases with membrane tension
  [[CLM]] - Increased membrane tension results in slower dynamics of clathrin coated structures.
  [[CLM]] - CG endocytosis is upregulated upon decrease of cell membrane tension.
  ```")
  (extract-from-code-block t)
  (extract-from-code-block tt)
  (extract-from-code-block n))

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

(defn delete-block [uid]
  (j/call-in js/window [:roamAlphaAPI :data :block :delete]
    (clj->js {:block {:uid uid}})))

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
  (when (some? block-str)
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
       remove-entry))))

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

(comment
  (extract-embeds "{{[[embed]]: ((oyU-xMonE))}}"))

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
         res (atom [top-parent])]
     (p pre struct)
     (p (str pre "open in sidebar?") open-in-sidebar?)
     (go
       (while (not-empty @stack)
          (let [cur                  (first @stack)
                {:keys [t u s o op
                        string
                        title
                        uid
                        order]}      cur
                new-uid              (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                parent               (first @res)
                u                    (or u uid)
                o                    (or o order)
                s                    (or s string)
                t                    (or t title)
                c                    (or (:c cur) (:children cur))
                args                 {:parent-uid parent
                                       :block-uid  (if (some? u ) u new-uid)
                                       :order      (if (some? o) o "last")
                                       :string     s
                                       :open      (if (some? op) op true)}]
              (swap! stack rest)
              (swap! stack #(vec (concat % (sort-by :order c))))
              ;(println "block-" string "-parent-" parent #_(first @res))
              (p (str pre "creating with args: " t  " -- " args))
              (cond
                (some? t) (<p! (create-new-page t (if (some? u) u  new-uid)))
                (some? s) (<p! (create-new-block-with-id args)))
              (swap! res rest)
              (swap! res #(vec (concat % (vec (repeat (count c)
                                                (if (some? u)
                                                  u
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

      (<p! (js/Promise. (fn [r rr]
                          (cb))))))))

(def gemini-safety-settings-struct
  {:s "Safety settings"
   :op false
   :c [{:s "Harassment"
        :c [{:s "Block some"}]}
       {:s "Hate Speech"
        :c [{:s "Block some"}]}
       {:s "Sexually Explicit"
        :c [{:s "Block some"}]}
       {:s "Dangerous Content"
        :c [{:s "Block some"}]}]})



(defn get-focused-block []
  (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
    (j/get :block-uid)))


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
                        :c [{:s "This is Dr. Akamatsu's biology lab at the University of Washington. Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.\n\nWe capture questions (QUE), hypotheses (HYP), results (RES), and sources (which begin with @) on separate pages in Roam. We call these discourse pages. Each page has a title summarizing the key insight, a body detailing relevant content, and hierarchical references (refs) linking to related pages. The refs show the lineage of ideas from one page to detailed explorations on another. By connecting the dots across pages, we maintain an audit trail of the evolution in our research.\n\nFor example, a QUE page may ask \"How does the Arp2/3 complex bind to actin filaments?\" This could link to a HYP page proposing a molecular binding mechanism as a hypothesis. The HYP page would in turn link to RES pages containing observations that support or oppose our hypothesis. The RES page links to a source, which can correspond to a published research article or our ongoing experiment or simulation.\n\nThe provided page data reflects this structure.  Each individual page is a map with keys `:title`, `:body` and `:refs`. The body content combines biology knowledge with our lab's own analyses and experimental data.\n\nGiven the following data from a page summarize it for me as an expert in this field. For your responses, make use of the linked references, and strive to be detailed and accurate. When possible, cite discourse page titles verbatim, surrounded in double square brackets, to ground your responses. \n\nData from page: "}]}]}
                        ;; :c [{:s "This is Dr. Akamatsu's biology lab at the University of Washington. Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.\n\nWe capture questions (QUE), hypotheses (HYP), and conclusions (CON) on separate pages in Roam. Each page has a title summarizing the key insight, a body elaborating on findings and literature, and hierarchical references (refs) linking to related pages. The refs show the lineage of ideas from one page to detailed explorations on another.\n\nFor example, a QUE page may ask \"How does the Arp2/3 complex bind to actin filaments?\" This could link to a HYP page proposing a molecular binding mechanism as a hypothesis. The HYP page would in turn link to CON pages concluding whether our hypothesis was supported or refuted.\n\nOur pages integrate knowledge from publications, data visualizations, and discussions with experts in the field. By connecting the dots across pages, we maintain an audit trail of the evolution in our research.\n\nThe provided page data reflects this structure, each individual page is a map with keys `:title`, `:body` and `:refs`. The body content combines biology expertise with our lab's own analyses and experimental data.\n\nGiven the following data from a page summarise it for me as  nexpert in this field. Use the linked references for your answers, go in depth.\n\nData from page: "}]}]}
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
                        :c [{:s "false"}]}
                       {:s "Extract query pages"
                        :c [{:s "false"}]}
                       {:s "Extract query pages ref?"
                        :c [{:s "false"}]}
                       {:s "Context for discourse graph suggestion"
                        :c [{:s "Based on our previous conversation propose some new discourse nodes.\nNote: \n\n 1. follow the following format, this is format of the following lines `node type - format to follow if the node is of this type`. For each suggestion put it on a newline.\n\n```javascript\n[[CON]] - {content}                // Use format for node type \"Conclusion\"\n[[RES]] - {content} - [[@Source]]  // Use format for node type \"Result\"\n[[HYP]] - {content}                // Use format for node type \"Hypothesis\"\n[[ISS]] - {content}                // Use format for node type \"Issue\"\n@{content}                         // Use format for node type \"Source\"\n[[EVD]] - {content} - [[@Source]]  // Use format for node type \"Evidence\"\n[[QUE]] - {content}                // Use format for node type \"Question\"\n[[CLM]] - {content}                // Use format for node type \"Claim\"```\n\n2. following the format does not mean degrading your answer quality. We want both follow the format and high quality suggestions. \n3. Please only reply with discourse node suggestions, not explanations, keep them high quality. "}]}]}
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
                             :c [{:s "Images without description"}]}]}]}
                  {:s "Discourse graph this page"
                   :c [{:s "Context"
                        :c [{:s "This is Dr. Akamatsu's biology lab at the University of Washington. Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.\n\nWe capture questions (QUE), hypotheses (HYP), and conclusions (CON) on separate pages in Roam. Each page has a title summarizing the key insight, a body elaborating on findings and literature, and hierarchical references (refs) linking to related pages. The refs show the lineage of ideas from one page to detailed explorations on another.\n\nFor example, a QUE page may ask \"How does the Arp2/3 complex bind to actin filaments?\" This could link to a HYP page proposing a molecular binding mechanism as a hypothesis. The HYP page would in turn link to CON pages concluding whether our hypothesis was supported or refuted.\n\nOur pages integrate knowledge from publications, data visualizations, and discussions with experts in the field. By connecting the dots across pages, we maintain an audit trail of the evolution in our research.\n\nBased on the data provided from the page(s), propose some new discourse nodes.\n\nNote: \n\n 1. follow the following format, this is format of the following lines `node type - format to follow if the node is of this type`\n\n```javascript\n [[CON]] - {content}\n [[RES]] - {content} - {Source}\n [[HYP]] - {content}\n[[ISS]] - {content}\n@{content}\n[[EVD]] - {content} - {Source}\n [[QUE]] - {content}\n[[CLM]] - {content}```\n\n2. following the format does not mean degrading your answer quality. We want both follow the format and high quality suggestions. \n3. Please only reply with discourse node suggestions, not explanations, keep them high quality. \n\nData from page: "}]}
                       {:s "Settings"
                        :c [{:s "Model"
                             :c [{:s "gpt-3.5"}]}
                            {:s "Temperature"
                             :c [{:s "0.9"}]}
                            {:s "Get linked refs"
                             :c [{:s "false"}]}
                            {:s "Extract query pages"
                             :c [{:s "false"}]}
                            {:s "Extract query pages ref?"
                             :c [{:s "false"}]}]}]}]}]}
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
        :c [{:s "false"}]}
       {:s "Extract query pages"
        :c [{:s "true"}]}
       {:s "Extract query pages ref?"
        :c [{:s "true"}]}]})



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
  {"gpt-4"            "gpt-4o"
   "gpt-4-vision"     "gpt-4o"
   "gpt-3.5"          "gpt-3.5-turbo-0125"
   "claude-3-sonnet"  "claude-3-sonnet-20240229"
   "claude-3-opus"    "claude-3-opus-20240229"
   "gemini"           "gemini"})

(defn model-type [model-name]
  (cond
    (str/starts-with? model-name "gpt")    :gpt
    (str/starts-with? model-name "claude") :claude
    (str/starts-with? model-name "gemini") :gemini
    :else                                  :unknown))


(def safety-map
  {"Block none"  "BLOCK_NONE"
   "Block few"   "BLOCK_ONLY_HIGH"
   "Block some" "BLOCK_MEDIUM_AND_ABOVE"
   "Block most" "BLOCK_LOW_AND_ABOVE"})


(defn get-safety-settings [chat-block-uid]
  (let [settings-uid      (:uid (get-child-with-str chat-block-uid "Settings"))
        harassment        (get-child-of-child-with-str settings-uid "Safety settings" "Harassment")
        hate-speech       (get-child-of-child-with-str settings-uid "Safety settings" "Hate Speech")
        sexually-explicit (get-child-of-child-with-str settings-uid "Safety settings" "Sexually Explicit")
        dangerous-content (get-child-of-child-with-str settings-uid "Safety settings" "Dangerous Content")]
    (p "safety settings" harassment hate-speech sexually-explicit dangerous-content)
    [{:category "HARM_CATEGORY_HARASSMENT"
      :threshold (get safety-map harassment)}
     {:category "HARM_CATEGORY_HATE_SPEECH"
      :threshold (get safety-map hate-speech)}
     {:category "HARM_CATEGORY_SEXUALLY_EXPLICIT"
      :threshold (get safety-map sexually-explicit)}
     {:category "HARM_CATEGORY_DANGEROUS_CONTENT"
      :threshold (get safety-map dangerous-content)}]))


(goog-define url-endpoint "")

(defn call-api [url messages settings callback]
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
      :gpt        (call-api   "https://roam-llm-chat-falling-haze-86.fly.dev/chat-complete"
                    messages settings callback)
      :claude     (call-api  "https://roam-llm-chat-falling-haze-86.fly.dev/chat-anthropic"
                    messages settings callback)
     :gemini     (call-api "https://roam-llm-chat-falling-haze-86.fly.dev/chat-gemini"
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
  (let [pre-prompt          (str (-> (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" "Graph overview default pre prompt" "Pre prompt")
                                   :children
                                   first
                                   :string))
        context-type-string? (string? initial-context)
        current-message      (if (and (not-empty initial-context) context-type-string?)
                               (atom (str pre-prompt "\n Initial context: \n " (extract-from-code-block initial-context)))
                               (atom ""))
        alternate-messages   (if (and (not-empty initial-context) context-type-string?)
                               (atom [])
                               (atom [{:role "user"
                                       :content (vec (concat
                                                       [{:type "text"
                                                         :text (str pre-prompt "\n Initial context: \n")}]
                                                       (vec initial-context)))}]))]
    (p (str pre "create alternate messages"))
    (p @alternate-messages)
    (doseq [msg messages]
      (let [msg-str (replace-block-uids (:string msg))]
        (if (str/starts-with? msg-str "**Assistant:** ")
          (do
            (swap! alternate-messages conj {:role    "user"
                                            :content (str (clojure.string/replace @current-message #"^\*\*User:\*\* " ""))})
            (swap! alternate-messages conj {:role    "assistant"
                                            :content (str (clojure.string/replace msg-str #"^\*\*Assistant:\*\* " ""))})
            (reset! current-message ""))
          (swap! current-message #(str % "\n" (extract-from-code-block (clojure.string/replace msg-str #"^\*\*User:\*\* " "")))))))
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
     [:div
       {:class-name "Classes.POPOVER_DISMISS_OVERRIDE"}
       render-comp]]]))
