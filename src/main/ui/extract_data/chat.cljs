(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
    [ui.utils :as utils :refer [markdown-image-pattern p extract-embeds uid->eid replace-block-uids q uid-to-block get-eid]]
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
  (extract-markdown-image "text at the start ![image](https://www.google.com) text at the end"))
(str/replace "text at the start ![image](https://www.google.com) text at the end" markdown-image-pattern "gm")

(defn find-blocks-with-images [node]
  (when-not (contains? skip-blocks-with-string (:string node))
    (let [block?          (some? (:string node))
          current-image? (if block? (extract-markdown-image (:string node)) false)
          uid            (:uid node)
          children       (or (:children node)
                           [])]
      (concat
        (if  current-image?
          [{:uid uid
            :string (:string node)
            :match current-image?}]
          [])
        (mapcat find-blocks-with-images children)))))



(defn get-all-images-for-node [node block?]
  (let [eid                (cond
                             (int? node) node
                             block?   (uid->eid node)
                             :else    (get-eid node))
        children           (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/uid])
                                        :in $ ?eid]
                                     eid))
        blocks-with-images (find-blocks-with-images children)]
    blocks-with-images))

(comment
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
                                              (when block-string
                                                [block-string])
                                              (extract-strings (if embed? embed? sorted-by-children))))]
     (p "Successfully extracted children for page or block with node: " node)
     {:plain-text plain-text})))


(comment
  (get-children-for "3yD8b4Oer" true)
  (get-children-for "4llnpJ5Ae" true)

  (time (get-children-for "[[ISS]] - scope other options for the LLM chat interface"))
 (get-children-for "7RBKIHk-V" true)
 (build-map (get-children-for "7RBKIHk-V" true))
 (get-children-for "1owvT89TK" true)
 (get-children-for "llm chat")

 (get-children-for "mJ6ZmSQip" true)
 (get-children-for "testing 5"))

(defn extract-ref-data-for [title ref-eid ref-data]
  (p "Inside extract ref data for particular page function")
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


(defn get-all-data-for [title get-linked-refs?]
  (p "Inside get all data for particular page function")
  (let [children (get-children-for title)]
    (merge
     {:title     title
      :body      (:plain-text children)}
     (when @get-linked-refs?
       {:refs (get-all-refs-for title)}))))


(defn data-for-pages [pages get-linked-refs?]
  (p "Inside extract data for pages function")
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
  (p "Inside extract data for blocks function")
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
  (data-for-pages [{:text "[[ISS]] - scope other options for the LLM chat interface"}] (atom false))


  (data-for-pages [
                   {:text
                    "[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
                    :text-uid "YEbfS-WDB",
                    :uid "YEbfS-WDB"}
                   {:text "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."}]
    (atom false))

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

