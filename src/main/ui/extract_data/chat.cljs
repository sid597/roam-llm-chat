(ns ui.extract-data.chat
  (:require
    [applied-science.js-interop :as j]
    [ui.utils :as utils :refer [replace-block-uids q uid-to-block get-eid]]
    [clojure.string :as str]))


(defn extract-strings [node]
  (let [current-string (:string node)
        children (or (:children node) [])]
    (concat
      (if current-string [current-string] [])
      (mapcat extract-strings children))))


(defn build-map [lst]
  (into {}
    (for [item lst]
      [(get item :string) (-> (clojure.string/join "\n" (extract-strings item))
                            (replace-block-uids))])))


(defn get-children-for [p]
  (let [eid               (if (int? p) p (get-eid p))
        raw-children-data (ffirst (q '[:find (pull ?eid [{:block/children ...} :block/string :block/order])
                                       :in $ ?eid]
                                    eid))
        sorted-by-children (sort-by :order (:children raw-children-data))
        plain-text         (replace-block-uids (str/join "\n " (extract-strings {:children sorted-by-children})))
        map-format         (build-map  sorted-by-children)]

    {:plain-text plain-text
     #_#_:map-format map-format}))

(defn extract-ref-data-for [title ref-eid ref-data]
  (let [ breadcrumbs (str/join " > " (map #(when (not-empty %)
                                             (replace-block-uids (:string %)))
                                       (-> ref-data
                                         :parents)))
        children     (:plain-text (get-children-for ref-eid))
        full-context (str/join " > " [title breadcrumbs children])]
    {:parents breadcrumbs
     :children children
     :page title
     :full-context full-context}))


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
      (let [ref-data (extract-ref-data-for ref-parent-title ref-eid ref-data)]
        (swap! res conj (str (:full-context ref-data) title))))
    @res))


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



(comment
  (data-for-pages [
                   {:text
                    "[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
                    :text-uid "YEbfS-WDB",
                    :uid "YEbfS-WDB"}
                   {:text "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."}]
    (atom false))

  (get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")

  (some? (is-a-page? "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.")))

