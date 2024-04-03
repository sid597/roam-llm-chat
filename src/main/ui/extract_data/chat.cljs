(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
    [cljs.core.async :as async :refer [<! >! go]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [ui.utils :as utils :refer [title->uid markdown-image-pattern p extract-embeds uid->eid replace-block-uids q uid-to-block get-eid]]
    [clojure.string :as str]))

(def skip-blocks-with-string #{"{{ chat-llm }}" "AI chats" "AI summary"})

(comment
  (contains? skip-blocks-with-string "{{ chat-llm }}"))


(defn extract-markdown-image [s]
  (let [matches (re-find markdown-image-pattern s)]
    (if matches
      {:text (nth matches 1)
       :url (nth matches 2)}
      false)))

(comment
  (extract-markdown-image "![image](https://www.google.com)")
  (extract-markdown-image "![image](https://www.google.com) text at the end")
  (extract-markdown-image "text at the start ![image](https://www.google.com) text at the end")
  (str/replace "text at the start ![image](https://www.google.com) text at the end" markdown-image-pattern "gm")
  (extract-markdown-image "![The image shows a bar graph labeled \"B\". The title of the graph is \"Branch angle distribution (cryo-ET)\" which indicates that it's likely displaying data acquired through cryo-electron tomography (cryo-ET), a technique used for structural analysis typically in a biological context.\n\nThe x-axis represents branch angle in degrees, ranging from 0 to just under 90 degrees. The y-axis represents the number of branches, with counts ranging from 0 to 25. The bars represent the frequency distribution of branch angles with most of the data concentrated around a branch angle between 60 and 75 degrees, as indicated by the taller bars in that range.\n\nThe graph also includes a label indicating the mean branch angle: \"Mean: 68 ± 9°\" which suggests the average branch angle is 68 degrees with a standard deviation of 9 degrees.](https://firebasestorage.googleapis.com/v0/b/firescript-577a2.appspot.com/o/imgs%2Fapp%2Fresultsgraph%2FOFNSuhLiaa.png?alt=media&token=88eca532-776c-4414-bcc3-62387cc55dd5)"))

(defn find-blocks-with-images [node no-description?]
  (when-not (contains? skip-blocks-with-string (:string node))
     (let [block?          (some? (:string node))
           current-image? (if block? (extract-markdown-image (:string node)) false)
           without-desc   (empty? (:text current-image?))
           uid            (:uid node)
           children       (or (:children node)
                            [])]
       (concat
         (if no-description?
           (if (and current-image? without-desc)
             [{:uid uid
               :string (:string node)
               :match current-image?}]
             [])
           (if current-image?
             [{:uid uid
               :string (:string node)
               :match current-image?}]
             []))
         (mapcat #(find-blocks-with-images % no-description?) children)))))



(defn get-all-images-for-node
  ([node block?]
   (get-all-images-for-node node block? false))
  ([node block? with-no-description-only?]
   (let [no-description?    (= "Images without description" with-no-description-only?)
         eid                (cond
                              (int? node) node
                              block?   (uid->eid node)
                              :else    (get-eid node))
         children           (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/uid])
                                         :in $ ?eid]
                                      eid))
         blocks-with-images (find-blocks-with-images children no-description?)]
     blocks-with-images)))

(comment
  (get-all-images-for-node "Test: Image to text" false true)
  (get-all-images-for-node "testing 3" false)
  (get-all-images-for-node "YNWR7PAne" true)
  (get-all-images-for-node "[[EVD]] - Cryo electron tomography of actin branches in SKMEL2 cells showed a branching angle of 68 ± 15 degrees - @serwas2022mechanistic" false)
  (get-all-images-for-node "[[EVD]] - NWASP was found around clusters of clathrin heavy chain by TIRF microscopy + super resolution microscopy - [[@leyton-puig2017flat]]" false)
  (get-all-images-for-node "[[QUE]] - What is the rate at which cofilin binds actin filaments?" false))


(defn extract-all-data [title]
  (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order :block/uid])
               :in $ ?eid]
            (get-eid title))))


(defn sort-children [node]
  (if (contains? node :children)
    (assoc node :children (vec (->> (:children node)
                                 (map sort-children)
                                 (sort-by :order))))
    node))

(defn extract-strings
  ([tree]
   (extract-strings tree false))
  ([tree extract-query-block?]
   (let [result (atom [])
         stack (atom [tree])]
       (while (not (empty? @stack))
         (let [node             (peek @stack)
               string           (replace-block-uids (:string node))
               block-ref?       (when string (re-seq (re-pattern "\\(\\(\\(?([^)]*)\\)?\\)\\)") (:string node)))
               query-block?     (= string "{{query block}}")
               refed-qry-block? (and query-block?
                                  (some? block-ref?))
               node-uid         (if refed-qry-block?
                                  (second (first block-ref?))
                                  (:uid node))
               current-image?   (if string (extract-markdown-image string) false)
               embed?           (when (some? string)
                                  (ffirst (not-empty (extract-embeds (:string node)))))
               children         (:children node)]
           (swap! stack pop)
           (when (and string
                   (not (contains? skip-blocks-with-string string)))
             (cond
               (and query-block?
                 extract-query-block?)      (do
                                              (let [query-result (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuerySync] node-uid)
                                                                   (js->clj :keywordize-keys true))]
                                                (p "query-result" query-result)
                                                (doseq [n query-result]
                                                  (let [ext [(-> (extract-all-data (:text n))
                                                               (sort-children))]]
                                                    (swap! stack conj (first ext))))))
               embed?                       (swap! result conj (:string embed?))
               current-image?               (str/replace
                                              string
                                              markdown-image-pattern
                                              (str " Image alt text: " (:text current-image?)))
               (and query-block?
                 (not extract-query-block?)) nil
               :else                         (swap! result conj string)))
           (doseq [child (reverse (sort-by :order children))]
             (when (and (not query-block?)
                      (not (contains? skip-blocks-with-string string)))
               (swap! stack conj child)))))
     (p "result" @result)
     @result)))


(defn get-children-for
  ([{:keys [node block? extract-query-pages?]}]
   (p "Inside get children for particular page or block function: " node)
   (let [eid               (cond
                             (int? node) node
                             block?   (uid->eid node)
                             :else    (get-eid node))
         _ (p "eid" eid)
         raw-children-data (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order :block/uid])
                                        :in $ ?eid]
                                     eid))
         embed?             (when block? (ffirst (not-empty (extract-embeds (:string raw-children-data)))))
         sorted-by-children (sort-children raw-children-data)
         block-string       (:string raw-children-data)
         extracted-strings  (extract-strings
                              (if embed? embed? sorted-by-children)
                              extract-query-pages?)
         _ (p "extracted-strings" extracted-strings)
         plain-text         (str/join "\n " (concat
                                              (when block-string
                                                [block-string])
                                              extracted-strings))]

     (p "Successfully extracted children for page or block with node: " node)
     (p "plain-text -->" plain-text)
     {:plain-text plain-text})))

(comment
  (get-children-for {:node "Test: extract query pages"
                     :extract-query-pages? true}))

(defn extract-ref-data-for [title ref-eid ref-data]
  (p "Inside extract ref data for particular page function")
  (let [crumbs-set (into #{} (map #(when (not-empty %)
                                     (replace-block-uids (:string %)))
                               (-> ref-data
                                 :parents)))
        breadcrumbs (when-not (empty? crumbs-set)
                      (str/join " > " crumbs-set))
        children     (:plain-text (get-children-for {:node ref-eid}))
        full-context (str/join " > " (concat [title
                                              children]
                                       (when-not (nil? breadcrumbs)
                                             [breadcrumbs])))]
    (p "Successfully extracted ref data for page: " title)
    (when (empty? (clojure.set/intersection crumbs-set skip-blocks-with-string))
      {:parents breadcrumbs
       :children children
       :page title
       :full-context full-context})))


(defn get-all-refs-for [title]
  (p "Inside get all refs for particular page function")
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
        ;(println "ref-data" ex-ref-data)
        (swap! res conj (str (:full-context ex-ref-data) title))))
    (p "Successfully extracted ref data for all pages ")
    @res))

(comment
  (get-all-refs-for "llm chat"))


(defn get-all-data-for [title get-linked-refs? extract-query-pages?]
  (p "Inside get all data for particular page function")
  (let [children (get-children-for {:node   title
                                    :extract-query-pages? extract-query-pages?})]
     (merge
      {:title     title
       :body      (:plain-text children)}
      (when @get-linked-refs?
        {:refs (get-all-refs-for title)}))))


(defn data-for-pages [pages get-linked-refs? extract-query-pages?]
  (p "Inside extract data for pages function")
  (let [res (atom [])]
    (doall
      (for [page pages]
        (swap! res (fn [old-res]
                     (let [page-data (get-all-data-for (:text page) get-linked-refs? extract-query-pages?)]
                       (conj old-res
                         (with-out-str
                           (print "\n")
                           (print page-data)
                           (print "\n"))))))))
    @res))


(defn data-for-blocks
  ([block-uids]
   (data-for-blocks block-uids false))
  ([block-uids extract-query-pages?]
   (p "Inside extract data for blocks function" extract-query-pages?)
   (let [res (atom [])]
     (doall
       (for [block-uid block-uids]
         (swap! res (fn [old-res]
                      (let [block-data (:plain-text (get-children-for {:node block-uid
                                                                       :block? true
                                                                       :extract-query-pages? extract-query-pages?}))]
                         (conj old-res
                             (with-out-str
                               (print "\n")
                               (print block-data)
                               (print "\n"))))))))
     @res)))


(comment
  (data-for-blocks ["G5U5UaV7F"] (atom true))
  (data-for-blocks ["1owvT89TK"] (atom false))
  (data-for-pages [{:text "llm chat"}] (atom true) (atom false))
  (data-for-pages [{:text "[[ISS]] - scope other options for the LLM chat interface"}] (atom false)(atom false))


  (data-for-pages [
                   {:text
                    "[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
                    :text-uid "YEbfS-WDB",
                    :uid "YEbfS-WDB"}
                   {:text "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."}]
    (atom false)
    (atom false))

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

