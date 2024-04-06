(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
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
             (p "string" string node-uid)
             (cond
               (and query-block?
                 extract-query-block?)      (let [query-result (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuerySync] node-uid)
                                                                 (js->clj :keywordize-keys true))]
                                              (p "query-result" query-result)
                                              (doseq [n query-result]
                                                (let [ext [(-> (extract-all-data (:text n))
                                                             (sort-children))]]
                                                  (swap! stack conj (first ext)))))
               embed?                       (swap! result conj (:string embed?))
               current-image?               (str/replace
                                              string
                                              markdown-image-pattern
                                              (str " Image alt text: " (:text current-image?)))
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
                              (or extract-query-pages? false))
         _ (p "extracted-strings" extracted-strings)
         plain-text         (str/join " \n " (concat
                                               (when (and block-string (not (int? node)))
                                                 [block-string])
                                               extracted-strings))]

     (p "Successfully extracted children for page or block with node: " node)
     {:plain-text plain-text})))


(comment
  (get-children-for {:node "Test: extract query pages"
                     :extract-query-pages? true}))


(defn extract-ref-data-for [title ref-eid ref-block-parents]
  (p "Inside extract ref data for particular page function")
  (let [crumbs-set (into #{} (map #(when (not-empty %)
                                     (replace-block-uids (:string %)))
                               ref-block-parents))
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


(defn get-all-refs-for [{:keys [title block?]}]
   (p "Inside get all refs for particular page function")
   (let [node-eid (if block?
                    (uid->eid title)
                    (get-eid title))
         refs (q '[:find ?title
                   :in $ ?n-eid
                   :where
                   [?d :block/refs ?n-eid]
                   [?d :block/parents ?node]
                   (not [?p :block/children ?node])
                   [?node :node/title ?title]]
                 node-eid)
         res  (atom [])]
     (p "refs" refs)
     (doseq [[index [ref-title]] (map-indexed vector refs)]
       (p "index" index "ref-title" ref-title)
       (let [uid  (title->uid ref-title)]
         (when-let [_ (j/call-in js/window [:roamjs :extension :queryBuilder :isDiscourseNode] uid)]
           (p "----This is discourse node ---" uid)
           (swap! res conj (str "Referenced discourse node "index " title: " ref-title
                             " \n and its page content: \n "
                             (:plain-text (get-children-for {:node ref-title}))
                             " \n ")))))
     (p "Successfully extracted ref data for all pages ")
     (first @res)))


(comment
  (get-all-refs-for {:title "Test: extract query pages"})
  (get-children-for {:node "Test: extract query pages"})

  (get-all-refs-for {:title "G5U5UaV7F"
                     :block? true})
  (get-all-refs-for {:title "KJfDPkNxG"
                     :block? true})
  (get-children-for {:node "[[QUE]] - Testing llm plugin"})

  (get-all-refs-for {:title "[[QUE]] - What is the stoichiometry of Hip1R binding to actin filaments?"})
  (get-all-refs-for {:title "Limit LLM Chat Linked References to dgraph nodes"}))


(defn get-all-data-for
  [{:keys [title get-linked-refs? block? extract-query-pages?]}]
  (p "Inside get all data for particular page function")
  (let [children (get-children-for {:node                 title
                                    :block?               (or block? false)
                                    :extract-query-pages? extract-query-pages?})]
     (merge
      (when (not block?)
        {:title title})
      {:body      (:plain-text children)}
      (when get-linked-refs?
        {:refs (get-all-refs-for {:title  title
                                  :block? (or block? false)})}))))


(defn data-for-nodes
  [{:keys [nodes get-linked-refs? block? extract-query-pages?]}]
  (p "Inside extract data for pages function")
  (let [res (atom [])]
    (doall
     (for [node nodes]
        (swap! res (fn [old-res]
                     (let [args      {:title                node
                                      :get-linked-refs?     (or get-linked-refs? false)
                                      :block?               (or block? false)
                                      :extract-query-pages? (or extract-query-pages? false)}
                           _ (p "args" args)
                           node-data (get-all-data-for args)]
                       (conj old-res
                          (with-out-str
                            (print "\n")
                            (print node-data)
                            (print "\n"))))))))
    @res))


(comment
  ;; ------------------

  ;; Extract without linked refs or query pages
  (data-for-nodes {:nodes ["Test: extract query pages"]})

  ;; Extract with linked refs
  (data-for-nodes {:nodes ["Test: extract query pages"]
                   :get-linked-refs? true})

  ;; Extract with query pages
  (data-for-nodes {:nodes ["Test: extract query pages"]
                   :extract-query-pages? true})


  ;; Extract with linked refs and query pages
  (data-for-nodes {:nodes ["Test: extract query pages"]
                   :get-linked-refs? true
                   :extract-query-pages? true})

  (data-for-nodes {:nodes ["UAXMYAZuB"]
                   :get-linked-refs? true
                   :extract-query-pages? true
                   :block? true})

  ;; ------------------


  (data-for-nodes {:title ["Limit LLM Chat Linked References to dgraph nodes"]
                   :get-linked-refs? true})
  (data-for-nodes {:title ["KJfDPkNxG"]
                   :get-linked-refs? true
                   :block? true})
  (data-for-nodes {:title ["1owvT89TK"]
                   :get-linked-refs? true
                   :block? true})
  (data-for-nodes {:title ["llm chat"]
                   :get-linked-refs? true})
  (data-for-nodes {:title ["[[ISS]] - scope other options for the LLM chat interface"]
                   :get-linked-refs? false})
  (data-for-nodes {:title ["[[ISS]] - scope other options for the LLM chat interface"
                           "[[EVD]] - test evd node isDiscourseNode - [[@source]]"]
                   :get-linked-refs? false})

  (data-for-nodes
    {:title ["[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
             "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."]
     :get-linked-refs? false})

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

