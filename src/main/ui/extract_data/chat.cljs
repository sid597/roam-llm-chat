(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
    [reagent.core :as r]
    [ui.utils :as utils :refer [is-a-page? uid->title title->uid markdown-image-pattern p extract-embeds uid->eid replace-block-uids q uid-to-block get-eid]]
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


(defn extract-strings [{:keys [tree extract-query-pages? only-pages?]}]
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
           ; (p "string" string node-uid)
            (cond
              (and query-block?
                extract-query-pages?)      (let [query-result (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuerySync] node-uid)
                                                                (js->clj :keywordize-keys true))]
                                             (p "query-result" query-result)
                                             (doseq [n query-result]
                                               (p " query res: "(:uid n) (:text n))
                                               (let [nstr  (:text n)
                                                     page? (some? (uid->title (:uid n)))]
                                                (cond
                                                  (not page?) (swap! result conj (replace-block-uids nstr))
                                                  only-pages? (swap! result conj (str "[[" nstr "]]"))
                                                  :else       (let [ext [(-> (extract-all-data nstr)
                                                                           (sort-children))]]
                                                                (swap! stack conj (first ext)))))))
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
    ;(p "result" @result)
    @result))


(defn get-children-for [{:keys [node block? extract-query-pages? only-pages?]}]
   (p "Inside get children for particular page or block function: " node)
   (let [eid               (cond
                             (int? node) node
                             block?   (uid->eid node)
                             :else    (get-eid node))
        ; _ (p "eid" eid)
         raw-children-data (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order :block/uid])
                                        :in $ ?eid]
                                     eid))
         embed?             (when block? (ffirst (not-empty (extract-embeds (:string raw-children-data)))))
         sorted-by-children (sort-children raw-children-data)
         block-string       (:string raw-children-data)
         extracted-strings  (extract-strings {:tree                 (if embed? embed? sorted-by-children)
                                              :extract-query-pages? (or extract-query-pages? false)
                                              :only-pages?          only-pages?})
        ; _ (p "extracted-strings" extracted-strings)
         plain-text         (str/join " \n " (concat
                                               (when (and block-string (not (int? node)))
                                                 [block-string])
                                               extracted-strings))]

    ; (p "Successfully extracted children for page or block with node: " node)
     (if only-pages?
       {:extracted-query-pages extracted-strings}
       {:plain-text plain-text})))


(comment
  (get-children-for {:node "Test: extract query pages"
                     :extract-query-pages? true})
  (get-children-for {:node "sIZw65xin"
                     :block? true
                     :get-linked-refs? false
                     :only-pages? true
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
           (swap! res conj (str "Referenced discourse node title: " ref-title
                             " \n and its page content: \n "
                             (:plain-text (get-children-for {:node ref-title}))
                             " \n ")))))
     (p "Successfully extracted ref data for all pages ")
     (clojure.string/join "\n" @res)))


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


(defn get-all-data-for [{:keys [title get-linked-refs? block? extract-query-pages? only-pages?]}]
  (p "Inside get all data for particular page function")
  (let [children (get-children-for {:node                 title
                                    :block?               block?
                                    :extract-query-pages? extract-query-pages?
                                    :only-pages?          only-pages?})]
      (merge
       (when (not block?)
         {:title title})
       {:body      (if only-pages?
                     (:extracted-query-pages children)
                     (:plain-text children))}
       (when get-linked-refs?
         (when-let [refs (get-all-refs-for {:title  title
                                            :block? (or block? false)})]
           {:refs refs})))))


(defn data-for-nodes [{:keys [nodes get-linked-refs? block? extract-query-pages? only-pages?]}]
  (p "Inside extract data for pages function" nodes get-linked-refs? block? extract-query-pages? only-pages?)
  (let [res (atom [])]
    (doseq [node nodes]
       (swap! res (fn [old-res]
                    (let [args      {:title                node
                                     :get-linked-refs?     (or get-linked-refs? false)
                                     :block?               (or block? false)
                                     :extract-query-pages? (or extract-query-pages? false)
                                     :only-pages?          (or only-pages? false)}
                          _ (p "args" args)
                          node-data (get-all-data-for args)]
                      (if only-pages?
                        node-data
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

  (data-for-nodes {:nodes                ["Test: extract query pages"]
                   :get-linked-refs?     true
                   :extract-query-pages? true
                   :only-pages?          true})

  (get-all-data-for {:title                "Test: extract query pages"
                     :get-linked-refs?     true
                     :extract-query-pages? true
                     :only-pages?          true})

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



(defn get-first-pass-context [child get-linked-refs? extract-query-pages? only-pages?]
  (let [cstr             (:string child)
        block-ref?       (when cstr (re-seq (re-pattern "\\(\\(\\(?([^)]*)\\)?\\)\\)") (:string child)))]

    (cond
      (some? (is-a-page? cstr))  (data-for-nodes {:nodes [(is-a-page? cstr)]
                                                  :get-linked-refs? get-linked-refs?
                                                  :extract-query-pages? extract-query-pages?
                                                  :only-pages? only-pages?})
      (some? block-ref?)         (let [block-uid (second (first block-ref?))]
                                   (data-for-nodes {:nodes                [block-uid]
                                                    :block?               true
                                                    :get-linked-refs?     get-linked-refs?
                                                    :only-pages?          only-pages?
                                                    :extract-query-pages? extract-query-pages?})))))



(defn extract-query-pages [context get-linked-refs? extract-query-pages? only-pages?]
  (p "extract query pages context: ")
  (let [res (atom [])]
    (doseq [child (:children context)]
      (p "child: " child)
      (let [context-with-query-pages (get-first-pass-context
                                       child
                                       get-linked-refs?
                                       extract-query-pages?
                                       only-pages?)
            _ (p "context with query pages: " context-with-query-pages)
            ext-context              (r/atom "")]
        (if only-pages?
          (do
            (doseq [cstr (:body context-with-query-pages)]
              (cond
                (some? (is-a-page? cstr)) (do
                                            (p "---" cstr)
                                            (let [page-data (clojure.string/join " \n " (data-for-nodes
                                                                                          {:nodes [(is-a-page? cstr)]
                                                                                           :get-linked-refs? get-linked-refs?
                                                                                           :extract-query-pages? extract-query-pages?}))]
                                              (swap! ext-context str " \n " page-data)))
                :else                     (do
                                            (p "normal string" cstr)
                                            (swap! ext-context str " \n " cstr))))
            (swap! res conj  (with-out-str
                               (print "\n")
                               (print (merge
                                        (when (some? (:title context-with-query-pages))
                                          {:title (:title context-with-query-pages)})
                                        {:body  @ext-context}
                                        (when (some? (:refs context-with-query-pages))
                                           {:refs (:refs context-with-query-pages)})))
                               (print "\n"))))
          (swap! res conj (first context-with-query-pages)))))
    (p "extracted the query pages")
    @res))


(comment
  (get-child-with-str "KScozVenE" "Context")
  (get-first-pass-context
    {:order 0, :string "[[Test: extract query pages]]", :uid "idyAjL4Xd"},
    true true false)
  ;; in results graph
  (extract-query-pages
    {:children [{:order 0, :string "((G5U5UaV7F))", :uid "idyAjL4Xd"}],}
    true true true)
  (extract-query-pages
    {:children [{:order 0, :string "[[Test: extract query pages]]", :uid "idyAjL4Xd"}],}
    true true false))

