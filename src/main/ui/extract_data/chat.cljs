(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
    [ui.utils :as utils :refer [uid->eid replace-block-uids q uid-to-block get-eid]]
    [clojure.string :as str]))

(def skip-blocks-with-string #{"{{ chat-llm }}" "AI chats"})

(contains? skip-blocks-with-string "{{ chat-llm }}")

(defn extract-strings [node]
  (when-not (contains? skip-blocks-with-string (:string node))
    (let [current-string (:string node)
          children (or (:children node) [])]
      (concat
        (if current-string [current-string] [])
        (mapcat extract-strings children)))))


(defn build-map [lst]
  (into {}
    (for [item lst]
       [(get item :string) (-> (clojure.string/join "\n" (extract-strings item))
                             (replace-block-uids))])))



(defn get-children-for
  ([p] (get-children-for p false))
  ([p block?]
   (let [eid               (cond
                             (int? p) p
                             block?   (uid->eid p)
                             :else    (get-eid p))
         raw-children-data (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order])
                                        :in $ ?eid]
                                     eid))
         sorted-by-children (sort-by :order (:children raw-children-data))
         block-string        (:string raw-children-data)
         plain-text         (replace-block-uids (str/join "\n " (concat
                                                                  (when block-string
                                                                    [block-string])
                                                                  (extract-strings {:children sorted-by-children}))))
         map-format         (build-map  sorted-by-children)]
     {:plain-text plain-text
      #_#_:map-format map-format})))

(comment
 (get-children-for "1owvT89TK" true)
 (get-children-for "llm chat"))

(defn extract-ref-data-for [title ref-eid ref-data]
  (let [crumbs-set (into #{} (map #(when (not-empty %)
                                     (replace-block-uids (:string %)))
                               (-> ref-data
                                 :parents)))
        breadcrumbs (when-not (empty? crumbs-set)
                      (str/join " > " crumbs-set))
        children     (:plain-text (get-children-for ref-eid))
        full-context (str/join " > " (concat [title
                                              children]
                                       (when-not (nil? breadcrumbs)
                                             [breadcrumbs])))]
    (when (empty? (clojure.set/intersection crumbs-set skip-blocks-with-string))
      {:parents breadcrumbs
       :children children
       :page title
       :full-context full-context})))


(defn get-all-refs-for [title]
  (let [refs (q '[:find ?title ?page  ?refs (pull ?refs [:block/string {:block/parents [:block/string]}
                                                         :block/uid
                                                         {:block/page [:node/title]}])

                  :in $ ?node
                  :where
                  [?n :node/title ?node]
                  [?refs :block/refs ?n]
                  [?refs :block/page ?page]
                  [?page :node/title ?title]]
               title)
        res  (atom [])]
    (doseq [[ref-parent-title
             page-eid
             ref-eid
             ref-data] refs]
      (let [ex-ref-data (extract-ref-data-for ref-parent-title ref-eid ref-data)]
        (println "ref-data" ex-ref-data)
        (swap! res conj (str (:full-context ex-ref-data) title))))
    @res))

(get-all-refs-for "llm chat")


(defn get-all-data-for [title get-linked-refs?]
  (let [children (get-children-for title)]
    (merge
     {:title     title
      :body      (:plain-text children)}
     (when @get-linked-refs?
       {:refs (get-all-refs-for title)}))))


(defn data-for-pages [pages get-linked-refs?]
  (let [res (atom [])]
    (doall
     (for [page pages]
       (swap! res (fn [old-res]
                    (let [page-data (get-all-data-for (:text page) get-linked-refs?)]
                     (conj old-res
                           (with-out-str
                             (print "\n")
                             (print page-data)
                             (print "\n"))))))))
    @res))



(defn data-for-blocks [block-uids]
  (let [res (atom [])]
    (doall
      (for [block-uid block-uids]
        (swap! res (fn [old-res]
                     (let [block-data (:plain-text (get-children-for block-uid true))]
                        (conj old-res
                            (with-out-str
                              (print "\n")
                              (print block-data)
                              (print "\n"))))))))
    @res))


(comment
  (data-for-blocks ["1owvT89TK"])
  (data-for-pages [{:text "llm chat"}] (atom true))

  (data-for-pages [
                   {:text
                    "[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
                    :text-uid "YEbfS-WDB",
                    :uid "YEbfS-WDB"}
                   {:text "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."}]
    (atom false))

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

