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


(defn extract-strings [node]
  (when-not (contains? skip-blocks-with-string (:string node))
    (let [block?          (some? (:string node))
          embed?         (when block? (ffirst (not-empty (extract-embeds (:string node)))))
          current-image? (if block? (extract-markdown-image (:string node)) false)
          current-string (cond
                           embed?         (:string embed?)
                           current-image? (str/replace
                                            (:string node)
                                            markdown-image-pattern
                                            (str " Image alt text: " (:text current-image?)))
                           :els           (:string node))
          children       (or (:children (if (some? embed?)
                                          embed?
                                          node))
                           [])]
      (concat
        (if  current-string
          [(replace-block-uids current-string)]
          [])
        (mapcat extract-strings children)))))


(defn build-map [lst]
  (into {}
    (for [item lst]
       [(get item :string) (-> (clojure.string/join "\n" (extract-strings item))
                             (replace-block-uids))])))


(defn sort-children [node]
  (if (contains? node :children)
    (assoc node :children (vec (->> (:children node)
                                 (map sort-children)
                                 (sort-by :order))))
    node))


(defn get-children-for
  ([node] (get-children-for node false))
  ([node block?]
   (p "Inside get children for particular page or block function: " node)
   (let [eid               (cond
                             (int? node) node
                             block?   (uid->eid node)
                             :else    (get-eid node))
         raw-children-data (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order])
                                        :in $ ?eid]
                                     eid))
         embed?             (when block? (ffirst (not-empty (extract-embeds (:string raw-children-data)))))
         sorted-by-children (sort-children raw-children-data)
         block-string       (:string raw-children-data)
         plain-text         (str/join "\n " (concat
                                              (when (and block-string (not (int? node)))
                                                [block-string])
                                              (extract-strings (if embed? embed? sorted-by-children))))]
     (p "Successfully extracted children for page or block with node: " node)
     {:plain-text plain-text})))


(comment
  (get-children-for "KJfDPkNxG" true)
  (get-children-for "3yD8b4Oer" true)
  (get-children-for "4llnpJ5Ae" true)

  (time (get-children-for "[[ISS]] - scope other options for the LLM chat interface"))
 (get-children-for "7RBKIHk-V" true)
 (build-map (get-children-for "7RBKIHk-V" true))
 (get-children-for "1owvT89TK" true)
 (get-children-for "llm chat")

 (get-children-for "mJ6ZmSQip" true)
 (get-children-for "testing 5"))

(defn extract-ref-data-for [title ref-eid ref-block-parents]
  (p "Inside extract ref data for particular page function")
  (let [crumbs-set (into #{} (map #(when (not-empty %)
                                     (replace-block-uids (:string %)))
                               ref-block-parents))
        breadcrumbs (when-not (empty? crumbs-set)
                      (str/join " > " crumbs-set))
        children     (:plain-text (get-children-for ref-eid))
        full-context (str/join " > " (concat [title
                                              children]
                                       (when-not (nil? breadcrumbs)
                                             [breadcrumbs])))]
    (p "Successfully extracted ref data for page: " title)
    (p "with full context --->" full-context)
    (when (empty? (clojure.set/intersection crumbs-set skip-blocks-with-string))
      {:parents breadcrumbs
       :children children
       :page title
       :full-context full-context})))


(defn get-all-refs-for
  ([title]
   (get-all-refs-for title false))
  ([title block?]
   (p "Inside get all refs for particular page function")
   (let [uid  (if block? title (title->uid title))
         refs (q '[:find ?refs (pull ?refs [:block/string {:block/parents [:block/string]}
                                            :block/uid
                                            {:block/page [:node/title :block/uid]}])
                   :in $ ?node
                   :where
                   [?n :block/uid ?node]
                   [?refs :block/refs ?n]]
                uid)
         res  (atom [])]
     (cljs.pprint/pprint refs)
     (doseq [[ref-eid
              ref-data] refs]
       (let [ref-parent-page   (-> ref-data :page :title)
             uid               (-> ref-data :page :uid)
             ref-block-parents (-> ref-data :parents)
             ex-ref-data       (extract-ref-data-for ref-parent-page ref-eid ref-block-parents)]
         (p "---uid :--- " uid (j/call-in js/window [:roamjs :extension :queryBuilder :isDiscourseNode] uid))
         (when-let [_ (j/call-in js/window [:roamjs :extension :queryBuilder :isDiscourseNode] uid)]
           (p "----This is discourse node ---" uid)
           (swap! res conj (str (:full-context ex-ref-data))))))
     (p "Successfully extracted ref data for all pages ")
     @res)))

(comment
  (get-all-refs-for "KJfDPkNxG" true)
  (get-all-refs-for "[[QUE]] - What is the stoichiometry of Hip1R binding to actin filaments?")
  (get-all-refs-for "Limit LLM Chat Linked References to dgraph nodes"))


(defn get-all-data-for
  ([title get-linked-refs?]
   (get-all-data-for title get-linked-refs? false))
  ([title get-linked-refs? block?]
   (p "Inside get all data for particular page function")
   (let [children (if block?
                    (get-children-for title true)
                    (get-children-for title))]
     (p "--" children)
     (merge
      (when (not block?)
        {:title title})
      {:body      (:plain-text children)}
      (when get-linked-refs?
        {:refs (if block?
                 (get-all-refs-for title true)
                 (get-all-refs-for title))})))))


(defn data-for-nodes
  ([nodes get-linked-refs?]
   (data-for-nodes nodes get-linked-refs? false))
  ([nodes get-linked-refs? block?]
   (p "Inside extract data for pages function")
   (let [res (atom [])]
     (doall
      (for [node nodes]
        (swap! res (fn [old-res]
                     (let [page-data (get-all-data-for node get-linked-refs? block?)]
                      (conj old-res
                            (with-out-str
                              (print "\n")
                              (print page-data)
                              (print "\n"))))))))
     @res)))


(comment
  (data-for-nodes ["Limit LLM Chat Linked References to dgraph nodes"] true)
  (data-for-nodes ["KJfDPkNxG"] true true)
  (data-for-nodes ["1owvT89TK"] true true)
  (data-for-nodes ["llm chat"] true)
  (data-for-nodes ["[[ISS]] - scope other options for the LLM chat interface"] false)
  (data-for-nodes ["[[ISS]] - scope other options for the LLM chat interface"
                   "[[EVD]] - test evd node isDiscourseNode - [[@source]]"] false)

  (data-for-nodes
    ["[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
     "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."]
    false)

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

