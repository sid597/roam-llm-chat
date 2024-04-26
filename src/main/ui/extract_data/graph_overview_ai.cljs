(ns ui.extract-data.graph-overview-ai
  (:require
    [applied-science.js-interop :as j]
    [ui.utils :as utils :refer [q]]))

(defn is-discourse-node [s]
  ;(println "-->" s)
  (let [node ["QUE" "CLM" "EVD" "RES" "ISS" "HYP" "EXP" "CON"]
        node-regex (re-pattern (str "\\[\\[" (clojure.string/join "\\]\\]|\\[\\[" node) "\\]\\]"))
        src-regex (re-pattern "^@[^\\s]+")]
    (if (or (re-find node-regex s)
          (re-find src-regex s))
      s
      nil)))

(defn discourse-nodess [s]
  (let [node ["QUE" "CLM" "EVD" "RES" "ISS" "HYP" "EXP" "CON"]
        node-regex (re-pattern (str "\\[\\[(" (clojure.string/join "|" node) ")\\]\\]"))
        src-regex (re-pattern "@[^\\s]+")] ; Removed the '^' to match '@' anywhere in the string
    (if (or (re-find node-regex s)
          (re-find src-regex s))
      s
      nil)))

(def test-strings (clojure.string/split-lines
                    "[[QUE]] - What molecular mechanisms enable the differential regulation of capping rates by CPI motif proteins in various cellular contexts?
                   [[HYP]] - CPI motif mutations in Aim21 and Bsp1 may alter their interaction with Capping Protein, thereby modulating the recruitment efficiency and activity at endocytic sites.
                   [[EVD]] - In the study by Lamb et al., CPI motif variants in Aim21 and Bsp1 showed differential effects on CP recruitment in vitro, suggesting that these motifs play a critical role in regulating actin dynamics. - [Lamb2023mechanism]
                   [[ISS]] - There is an unresolved question about the role of twinfilin in the recruitment sequence and its interaction with CP, as the in vitro assays did not show significant effects while cellular assays suggested critical involvement.
                   [[CON]] - The recruitment of capping protein by CPI motif-containing proteins is crucial for maintaining actin dynamics at endocytic sites. Disruptions in this process can significantly affect cellular functions such as migration and internalization.
                   [[RES]] - Opto-genetics combined with mechanical stress applications, as detailed in the @debelly2023cell study, opens new avenues for studying cellular responses to physical forces. - [Matt Akamatsu]
                   [[CLM]] - The involvement of Bar-domain proteins in sensing membrane curvature as described in @sitarska2023sensing suggests a broader role for these proteins in cellular architecture beyond just curvature recognition.
                   [[QUE]] - How does the interaction between the actin cortex and the cell membrane influence the propagation of mechanical forces across the cell?
                   [[HYP]] - Enhancing membrane tension globally through combined manipulation of the membrane and actin cortex could lead to novel insights into cellular mechanics and potentially innovative therapeutic strategies.
                   [[EVD]] - The experiment by Debelly et al. showed that tension does not propagate across the membrane unless the actin cortex is also engaged, highlighting the importance of their connection. - [Matt Akamatsu]
                   [[CLM]] - SNX33's accumulation at sites of cell-cell contact or obstacles may inhibit actin polymerization, suggesting a mechanism where SNX33 regulates cell directional migration by altering local actin dynamics."))

(map discourse-nodess test-strings)


(comment
  (is-discourse-node "a [QUE]] - Is this a question?")
  (is-discourse-node "[[QUE]] - How do Hip1R binding kin)etics and periodicity affect endocytic actin architecture and integrity?")
  (is-discourse-node "@Simulations varying "))

(defn generate-in-query-pattern [depth]
  (let [queries (concat
                  [[(symbol  "?e"  ) :node/title (symbol (str "?page"))]]
                  (mapcat (fn [d]
                            (let [base-index (- d 1)]
                              [[(symbol (str "?d" base-index)) :block/refs (symbol
                                                                             (if (= 0 base-index)
                                                                               "?e"
                                                                               (str "?r"  (- base-index 1))))]
                               [(symbol (str "?d" base-index)) :block/page (symbol (str "?r" base-index))]]))
                    (range 1 (inc depth)))
                  [[(symbol (str "?r" (- depth 1) )) :node/title (symbol (str "?title"))]])]
    (pr-str (vec (concat [:find (symbol (str "?title" )) :in '$ '?page :where]
                   queries)))))

(comment (generate-in-query-pattern 2))

(defn get-in-refs [page-name depth]
  (let [qry-res  (q
                   (generate-in-query-pattern depth)
                   page-name)
        filtered-discourse-nodes (reduce (fn [acc x]
                                           (if (some? (is-discourse-node (first x)))
                                             (conj acc (first x))
                                             acc))
                                   #{}
                                   qry-res)]

   ; (println "In qry res count:" (count qry-res))
    filtered-discourse-nodes))

(comment
  (get-in-refs
    "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"
    0))

(defn generate-out-query-pattern [depth]
  (let [queries (concat
                  [[(symbol  "?p"  ) :node/title (symbol (str "?page"))]]
                  (mapcat (fn [d]
                            (let [base-index (- d 1)]
                              [[(symbol (str "?d" base-index)) :block/refs (symbol (str "?e"  base-index))]
                               [(symbol (str "?d" base-index)) :block/page (symbol
                                                                             (if (= 0 base-index)
                                                                               "?p"
                                                                               (str "?e" (- base-index 1))))]]))
                    (range 1 (inc depth)))
                  [[(symbol (str "?e" (- depth 1) )) :node/title (symbol (str "?title"))]])]
    (pr-str (vec (concat [:find (symbol (str "?title" )) :in '$ '?page :where]
                   queries)))))



(comment
  (generate-out-query-pattern 3)

  (q
    (generate-out-query-pattern 1)
    "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"))

(defn get-out-refs [page-name depth]
  (let [qry-res  (q
                   (generate-out-query-pattern depth)
                   page-name)
        filtered-discourse-nodes (reduce (fn [acc x]
                                           (if (some? (is-discourse-node (first x)))
                                             (conj acc (first x))
                                             acc))
                                   #{}
                                   qry-res)]
    ;(println "Out qry res count:" (count qry-res))
    filtered-discourse-nodes))


(comment
  (get-out-refs
    "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"
    1))


(defn get-explorer-pages []
  ;(println "get explorer pages")
  (let [page-name (str (js->clj (first (j/call-in js/window [:roamAlphaAPI :ui :graphView :wholeGraph :getExplorePages]))))
        [in out]  (->> (j/call js/document :querySelectorAll ".bp3-slider-handle > .bp3-slider-label")
                    (map (fn [x]
                           (j/get x :textContent))))
        in-pages (if (> in 0)
                   (get-in-refs page-name  (js/parseInt in))
                   #{})
        out-pages (if (> out 0)
                    (get-out-refs page-name (js/parseInt out))
                    #{})]
    ;(println "in out - pages" in out)
    {:page-name page-name
     :in-pages in-pages
     :out-pages out-pages}))


(comment
  (get-explorer-pages))