(ns ui.extract-data.dg
  (:require
    ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
    [clojure.string :refer [starts-with?]]
    [applied-science.js-interop :as j]
    [ui.utils :as util :refer [q]]
    [clojure.string :as str]))

(def evd-pattern (re-pattern (str "^\\[\\[EVD\\]\\] - (.*?) - (.*?)$")))

(defn node-pattern [node]
  (re-pattern (str "^\\[\\[" node "\\]\\] - (.*?)$")))

(def source-pattern (re-pattern "^@[^\\s]+$"))

(defn extract-all-discourse-for [node]
  (let [node-regex (cond
                     (= "EVD" node) evd-pattern
                     :else          (node-pattern node))]
    (flatten (q '[:find (pull ?e [:node/title :block/uid])
                  :in $ ?node-regex
                  :where
                  [?e :node/title ?tit]
                  [(re-find ?node-regex ?tit)]]
               node-regex))))


(defn extract-all-sources []
  (into [] (flatten (q '[:find
                         (pull ?node [:block/string :node/title :block/uid])
                         :where
                         [(re-pattern "^@(.*?)$") ?.*?$-regex]
                         [?node :node/title ?node-Title]
                         [(re-find ?.*?$-regex ?node-Title)]]))))


(def all-dg-nodes
  (let [cnt (atom [])
        nodes ["QUE" "CLM" "EVD" "RES" "ISS" "HYP" "CON"]]
    (doseq [node nodes]
      (let [total  (extract-all-discourse-for node)]
        (swap! cnt concat total)))
    (into [] (concat (into [] @cnt) (extract-all-sources)))))


(comment
  all-dg-nodes
  (count all-dg-nodes))

(comment
  (extract-all-discourse-for "CLM")
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

(defn hypothesis-informedBy-issue []
  (q '[:find (pull ?hyp-page [:node/title :block/uid])
       ?rel
       (pull ?iss-page [:node/title :block/uid])
       :in $ ?hyp-regex ?iss-regex ?rel
       :where [?hyp-page :node/title ?hyp-t]
       [(re-find ?hyp-regex ?hyp-t)]
       [?parent-block :block/refs ?hyp-page]
       [?parent-block :block/children ?block]
       [?iss-page :node/title ?iss-t]
       [?block :block/refs ?iss-page]
       [(re-find ?iss-regex ?iss-t)]]
    (node-pattern "HYP")
    (node-pattern "ISS")
    "InformedBy"))

(comment
  (hypothesis-informedBy-issue))

(defn extract-all-inform-discourse [source destination]
  (let [source-node-regex (if (= "EVD" source)
                            evd-pattern
                            (node-pattern source))
        destination-node-regex (if (= "EVD" destination)
                                 evd-pattern
                                 (node-pattern destination))]
    (->> (q '[:find (pull ?page [:node/title :block/uid])
              (pull ?parent-page [:node/title :block/uid])
              :in $ ?source-regex ?destination-regex
              :where
              [?page :node/title ?source-tit]
              [(re-find ?source-regex ?source-tit)]
              [?block :block/refs ?page]
              [?block :block/page ?parent-page]
              [?parent-page :node/title ?destination-tit]
              [(re-find ?destination-regex ?destination-tit)]]
           source-node-regex
           destination-node-regex)
      (map (fn [[source destination]]
             [destination "InformedBy" source])))))


(defn extract-all-from-to-relation [source destination rel]
  (let [source-node-regex (if (= source "EVD")
                            evd-pattern
                            (node-pattern source))
        destination-node-regex (if (= destination "EVD")
                                 evd-pattern
                                 (node-pattern destination))]
    (->> (q '[:find (pull ?source-page [:node/title :block/uid])
              ?rel
              (pull ?destination-page [:node/title :block/uid])
              :in $ ?source-node-regex ?destination-node-regex ?rel
              :where
              [?source-page :node/title ?source-t]
              [(re-find ?source-node-regex ?source-t)]
              [?parent-block :block/refs ?source-page]
              [?parent-block :block/children ?support-block]
              [?support-block :block/string ?rel]
              [?support-block :block/children ?block]
              [?destination-page :node/title ?destination-t]
              [?block :block/refs ?destination-page]
              [(re-find ?destination-node-regex ?destination-t)]]
           source-node-regex
           destination-node-regex
           rel))))

(comment
  (extract-all-inform-discourse "RES" "QUE")
  (extract-all-from-to-relation "CON" "RES"  "[[SupportedBy]]"))

(defn claim-informs-question []
  (->> (q '[:find (pull ?clm-page [:node/title :block/uid])
            ?rel
            (pull ?que-page [:node/title :block/uid])
            :in $ ?clm-regex ?que-regex ?rel
            :where [?que-page :node/title ?que-t]
            [(re-find ?que-regex ?que-t)]
            [?parent-block :block/refs ?que-page]
            [?parent-block :block/children ?block]
            [?clm-page :node/title ?clm-t]
            [?block :block/refs ?clm-page]
            [(re-find ?clm-regex ?clm-t)]]
         (node-pattern "CLM")
         (node-pattern "QUE")
         "InformedBy")
    (map (fn [[source rel destination]]
           [destination rel source]))))



(comment
  (reduce (fn [arr x]
            (conj arr (:title (first x))))
    []
    (claim-informs-question))

  (reduce (fn [arr x]
            (conj arr (:title (first x))))
    []
    (extract-all-inform-discourse "CLM" "QUE"))

  (extract-all-from-to-relation "EVD" "CLM" "[[Supports]]"))


(defn extract-source [s]
  (let [pattern (re-pattern (str "\\[\\[@[^\\s]+\\]\\]"))
        match (when (not (nil? s)) (re-find pattern s))]
    (when match
      (subs match 2 (- (count match) 2)))))

(defn get-evd-src [str]
  (let [matches (re-seq evd-pattern str)]
    (last (last matches))))

(defn get-block-data [eid]
  (ffirst (q '[:find (pull ?eid [:block/string :block/uid :block/parents :create/user :create/time
                                 :block/refs])
               :in $ ?eid]
            eid)))

(defn get-eid [title]
  (ffirst (q '[:find ?eid
               :in $ ?title
               :where [?eid :node/title ?title]]
            title)))

(defn get-children-with-str [eid]
  (let [result (ffirst (q '[:find (pull ?eid [{:block/children [:db/id :block/order :block/string]}])
                            :in $ ?eid]
                        eid))
        sorted-children (sort-by :order (:children result))]
    (map (fn [d]
           [(:id d) (:string d)]) sorted-children)))

(defn uid-to-block [uid]
  (q '[:find (pull ?e [*])
       :in $ ?uid
       :where [?e :block/uid ?uid]]
    uid))

(defn replace-block-uids [block-str]
  (let [re (re-pattern "\\(\\(\\(?([^)]*)\\)?\\)\\)")
        matches (re-seq re block-str)]
    (reduce (fn [s [whole-match uid]]

              (let [replace-str (str
                                  (:string (ffirst (uid-to-block uid))))

                    replacement (if (starts-with? whole-match "(((")
                                  (str "(" replace-str ")")
                                  replace-str)]
                (clojure.string/replace s whole-match replacement)))
      block-str
      matches)))


(defn get-children-eid-map [eid]
  (when eid
    (let [result (ffirst (q '[:find (pull ?eid [{:block/children [:db/id :block/order :block/string]}])
                              :in $ ?eid]
                           eid))
          sorted-children (sort-by :order (:children result))]
      (reduce
        (fn [m d]
          (assoc m (replace-block-uids (:string d)) (:id d)))
        {}
        sorted-children))))


(defn get-paper-title [paper-name]
  (let [paper-children (into [] (get-children-eid-map (get-eid paper-name)))
        metadata?      (first (reduce (fn [arr [k v]]
                                        (if (= "metadata::" k)
                                          (conj arr v)
                                          arr))
                                []
                                paper-children))
        title? (if (nil? metadata?)
                 (first (reduce (fn [arr [k v]]
                                  (if (starts-with? k "Title:: ")
                                    (conj arr k)
                                    arr))
                          []
                          paper-children))
                 (replace-block-uids (second (first (get-children-with-str metadata?)))))]

    title?))


(defn evd-src-nodes []
  (reduce (fn [arr node]
            (let [evd (:title node)
                  src (extract-source (get-evd-src evd))]
              (if (nil? src)
                arr
                (conj arr [node
                           "Source"
                           {:uid (:uid (get-block-data (get-eid src)))
                            :title (if (some? (get-paper-title src))
                                     (get-paper-title src)
                                     src)}]))))
    []
    (extract-all-discourse-for "EVD")))


(comment
  (get-evd-src (:title (first (extract-all-discourse-for "EVD"))))
  (extract-source (get-evd-src (:title (first (extract-all-discourse-for "EVD")))))
  (get-paper-title "@miki2000irsp53")
  (count (evd-src-nodes))
  (count (extract-all-inform-discourse "EVD" "QUE"))
  (flatten (apply concat [(evd-src-nodes)(extract-all-inform-discourse "EVD" "QUE")])))




(defn get-node-regex [node]
  (if (= node "EVD")
    evd-pattern
    (node-pattern node)))


(defn extract-node-where-source-is-regex [destination-node source-node rel edge-name]
  (let [source-node-regex (get-node-regex source-node)]
    (->> (q '[:find (pull ?source-page [:node/title :block/uid])
              (pull ?destination-page [:node/title :block/uid])
              :in $ ?source-node-regex ?destination-node ?rel
              :where
              [?source-page :node/title ?source-t]
              [(re-find ?source-node-regex ?source-t)]
              [?parent-block :block/refs ?source-page]
              [?parent-block :block/children ?rel-block]
              [?rel-block :block/string ?rel]
              [?rel-block :block/children ?block]
              [?destination-page :node/title ?destination-node]
              [?block :block/refs ?destination-page]]
           source-node-regex
           destination-node
           rel)
      (map (fn [[source destination]]
             [destination edge-name source])))))

(defn extract-node-where-destination-is-regex [source-node destination-node rel edge-name]
  (let [destination-node-regex (get-node-regex destination-node)]
    (->> (q '[:find (pull ?source-page [:node/title :block/uid])
              (pull ?destination-page [:node/title :block/uid])
              :in $ ?source-node ?destination-node-regex ?rel
              :where
              [?source-page :node/title ?source-node]
              [?parent-block :block/refs ?source-page]
              [?parent-block :block/children ?support-block]
              [?support-block :block/string ?rel]
              [?support-block :block/children ?block]
              [?destination-page :node/title ?destination-t]
              [?block :block/refs ?destination-page]
              [(re-find ?destination-node-regex ?destination-t)]]
           source-node
           destination-node-regex
           rel)
      (map (fn [[source destination]]
             [source edge-name destination])))))


(defn extract-inform-node-where-destination-is-regex [source-node destination rel-name]
  (let [destination-node-regex (get-node-regex destination)]
    (->> (q '[:find (pull ?page [:node/title :block/uid])
              (pull ?dg [:node/title :block/uid])
              :in $ ?source-node ?destination-regex
              :where
              [?page :node/title ?source-node]
              [?dg :node/title ?destination-tit]
              [(re-find ?destination-regex ?destination-tit)]
              [?block :block/refs ?dg]
              [?block :block/page ?page]]
           source-node
           destination-node-regex)
      (map (fn [[source destination]]
             [source rel-name destination])))))


(defn extract-inform-node-where-source-is-regex [destination source rel-name]
  (let [source-node-regex (get-node-regex source)]
    (->> (q '[:find (pull ?page [:node/title :block/uid])
              (pull ?dg [:node/title :block/uid])
              :in $ ?source-regex ?destination-node
              :where
              [?page :node/title ?source-tit]
              [(re-find ?source-regex ?source-tit)]
              [?dg :node/title ?destination-node]
              [?block :block/refs ?dg]
              [?block :block/page ?page]]
           source-node-regex
           destination)
      (map (fn [[source destination]]
             [destination rel-name source])))))


(defn informs-in-a-page-destination-regex [source destination name]
  (let [destination-node-regex (get-node-regex destination)]
    (->> (q '[:find
              (pull ?page  [:node/title :block/uid])
              (pull ?destination-page [:node/title :block/uid])
              :in $ ?source ?destination-regex
              :where
              [?page :node/title ?source]
              [?block :block/refs ?page]
              [?block :block/children ?potential]
              [?destination-page :node/title ?destination-node]
              [?potential :block/refs ?destination-page]
              [(re-find ?destination-regex ?destination-node)]]
           source
           destination-node-regex)
      (map (fn [[s d]]
             [s name d])))))



(defn informs-in-a-page-source-is-regex [destination source name]
  (let [source-node-regex (get-node-regex source)]
    (->> (q '[:find
              (pull ?page  [:node/title :block/uid])
              (pull ?destination-page [:node/title :block/uid])
              :in $ ?source-regex ?destination-node
              :where
              [?page :node/title ?source-tit]
              [(re-find ?source-regex ?source-tit)]
              [?block :block/refs ?page]
              [?block :block/children ?potential]
              [?destination-page :node/title ?destination-node]
              [?potential :block/refs ?destination-page]]
           source-node-regex
           destination)
      (map (fn [[s d]]
             [d name s])))))

(defn hyp-informed-by-que []
  [])
(defn que-infors-hyp []
  [])


(def node-pattern-match (re-pattern "^\\[\\[(.*?)\\]\\] - (.*?)$"))

(defn determine-node-type [s]
  (cond
    (re-matches source-pattern s) :source
    :else (let [node-match (re-matches node-pattern-match s)]
            (if node-match
              (keyword (second node-match))
              :unknown))))


(defn get-all-discourse-node-from-akamatsu-graph-for [node]
  (let [ntype (determine-node-type node)
        data  (case ntype
                :RES (concat []
                       (extract-node-where-destination-is-regex node "HYP" "[[Supports]]" "Supports")
                       (extract-node-where-source-is-regex      node "HYP" "[[SupportedBy]]" "Supports")
                       (extract-node-where-source-is-regex      node "CON" "[[SupportedBy]]" "Supports")
                       (extract-node-where-source-is-regex      node "CON" "[[OpposedBy]]" "Opposes")
                       (extract-node-where-source-is-regex      node "HYP" "[[OpposedBy]]" "Opposes")
                       (extract-inform-node-where-source-is-regex node "QUE" "Informs"))
                :EVD (concat []
                       (extract-node-where-destination-is-regex  node "CLM" "[[Supports]]" "Supports")
                       (extract-node-where-source-is-regex       node "CLM" "[[SupportedBy]]" "Supports")
                       (extract-node-where-destination-is-regex  node "HYP" "[[Supports]]" "Supports")
                       (extract-node-where-source-is-regex       node "HYP" "[[SupportedBy]]" "Supports")
                       (extract-node-where-source-is-regex       node "CLM" "[[OpposedBy]]" "Opposes")
                       (extract-node-where-source-is-regex       node "HYP" "[[OpposedBy]]" "Opposes")
                       (extract-node-where-destination-is-regex  node "EVD" "[[Supports]]" "Supports")
                       (extract-node-where-source-is-regex       node "EVD" "[[Supports]]" "Supports")
                       (extract-node-where-source-is-regex       node "EVD" "[[Reproduces]]" "Reproduces")
                       (extract-node-where-destination-is-regex  node "EVD" "[[Reproduces]]" "Reproduces")
                       (extract-inform-node-where-source-is-regex node "QUE" "Informs")
                       (extract-inform-node-where-source-is-regex node "HYP" "Informs"))
                :QUE (concat []
                       #_(que-infors-hyp)
                       (informs-in-a-page-destination-regex node "CLM" "InformedBy")
                       (extract-inform-node-where-destination-is-regex node "EVD" "InformedBy")
                       (extract-inform-node-where-destination-is-regex node "RES" "InformedBy"))

                :ISS (concat []
                       (extract-inform-node-where-source-is-regex node "HYP" "InformedBy")
                       (informs-in-a-page-source-is-regex node "HYP" "InformedBy"))

                :CLM (concat []
                       (informs-in-a-page-source-is-regex         node "QUE" "Informs")
                       (extract-node-where-destination-is-regex  node "EVD" "[[SupportedBy]]" "SupportedBy")
                       (extract-node-where-source-is-regex       node "EVD" "[[Supports]]" "SupportedBy")
                       (extract-node-where-destination-is-regex  node "EVD" "[[OpposedBy]]" "OpposedBy"))
                :CON (concat []
                       (extract-node-where-destination-is-regex node "RES" "[[SupportedBy]]" "SupportedBy")
                       (extract-node-where-destination-is-regex node "RES" "[[OpposedBy]]" "OpposedBy"))
                :HYP (concat []
                       #_(hyp-informed-by-que)
                       (extract-inform-node-where-destination-is-regex node "EVD" "InformedBy")
                       (extract-inform-node-where-destination-is-regex node "ISS" "Informs")
                       (informs-in-a-page-destination-regex            node "ISS" "Informs")
                       (extract-node-where-destination-is-regex node "EVD" "[[OpposedBy]]" "OpposedBy")
                       (extract-node-where-destination-is-regex node "RES" "[[OpposedBy]]" "OpposedBy")
                       (extract-node-where-destination-is-regex node "EVD" "[[SupportedBy]]" "SupportedBy")
                       (extract-node-where-source-is-regex      node "EVD" "[[Supports]]" "SupportedBy")
                       (extract-node-where-source-is-regex      node "RES" "[[Supports]]" "SupportedBy")
                       (extract-node-where-destination-is-regex node "RES" "[[SupportedBy]]" "SupportedBy"))
                "default")
        nodes (reduce (fn [acc [source _ target]]
                        (-> acc
                         (assoc (:title source) {:uid (:uid source)
                                                 :title (:title source)
                                                 :type (determine-node-type (:title source))})
                         (assoc (:title target) {:uid (:uid target)
                                                 :title (:title target)
                                                 :type (determine-node-type (:title target))})))
                {}
                data)]
    {:nodes nodes
     :edges data}))


(defn convert-to-cytoscape-edges [data]
  (mapv (fn [[source label target]]
          {:data {:id (str (:uid source) "-" (:uid target))
                  :source (:uid source)
                  :target (:uid target)
                  :label label}
           :group "edges"})
    data))

(comment
 (def n2 "[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules")
 (def n3 "[[QUE]] - Does actin polymerization adjacent to CLICs help to form CLIC tubules or scission of CLICs?")
 (get-all-discourse-node-from-akamatsu-graph-for n3)
 (concat [] (get-all-discourse-node-from-akamatsu-graph-for n2)
  (get-all-discourse-node-from-akamatsu-graph-for n3))
 #_(cljs.pprint/pprint (convert-to-cytoscape-edges (get-all-discourse-node-from-akamatsu-graph-for n3))))
