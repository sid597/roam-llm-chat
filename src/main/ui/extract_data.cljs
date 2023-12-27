(ns ui.extract-data
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]))

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

(def roam-pages [{
                  :text "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**",
                  :text-uid "TVZUdVlcj"
                  :uid "TVZUdVlcj"}

                 {
                  :text "[[ISS]] - Look for opposite-strand binding of arp2/3 complex on an actin filament filament?",
                  :text-uid "bN071CLdV",
                  :uid "bN071CLdV"}

                 {
                  :text "@Simulations varying Arp2/3 complex periodicity on endocytic actin filaments",
                  :text-uid "W08QSZAKF"
                  :uid "W08QSZAKF"}

                 {
                  :text "[[RES]] - The number of filaments per cluster plateaued at about 15 filaments after 10 nm actin lattice size - [[@Simulations varying Arp2/3 complex periodicity on endocytic actin filaments]]",
                  :text-uid "XSGDmwLn-",
                  :uid "XSGDmwLn-"}

                 {
                  :text "[[ISS]] - We should make a metric of actin radial asymmetry",
                  :text-uid "Rv1Bja1NI",
                  :uid "Rv1Bja1NI"}

                 {
                  :text "[[RES]] - For Arp2/3 complex binding periodicity values <= 8.25 nm, simulated endocytic internalization was > 50 nm - [[@Simulations varying Arp2/3 complex periodicity on endocytic actin filaments]]",
                  :text-uid "5dWHrEExv",
                  :uid "5dWHrEExv"}

                 {
                  :text "[[ISS]] - Report binding periodicity in endocytic actin simulations",
                  :text-uid "VOTx1blmq",
                  :uid "VOTx1blmq"}

                 {
                  :text "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?",
                  :text-uid "UxA7wfVez",
                  :uid "UxA7wfVez"}

                 {
                  :text "[[QUE]] - What is the stoichiometry of Arp2/3 complex binding to actin filaments?",
                  :text-uid "LHDUx2MOQ",
                  :uid "LHDUx2MOQ"}

                 {
                  :text "[[CON]] - For realistic values of Arp2/3 complex binding periodicity, internalization remains robust while the number of filaments per cluster decreases closer to experimentally determined values",
                  :text-uid "fo1Xw4SNo",
                  :uid "fo1Xw4SNo"}])



(defn remove-entry [block-str]
  (let [patterns ["Entry:SmartBlock:"
                  "\\{\\{Create Today's Entry:SmartBlock"
                  "\\{\\{Pick A Day:SmartBlock"
                  "\\#.sticky"]
        regex-pattern (str/join "|"
                        (map #(str % ".*?(\\s|$)") patterns))]
    (str/replace block-str (re-pattern regex-pattern) "")))

(defn uid-to-block [uid]
  (q '[:find (pull ?e [*])
       :in $ ?uid
       :where [?e :block/uid ?uid]]
    uid))


(defn replace-block-uids [block-str]
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
      remove-entry)))

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


(defn get-eid [title]
  (ffirst (q '[:find ?eid
               :in $ ?title
               :where [?eid :node/title ?title]]
            title)))

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


(defn get-all-data-for [title]
  (let [children (get-children-for title)
        refs     (get-all-refs-for title)]
    {:title     title
     :body (:plain-text children)
     :refs refs}))


(defn data-for-pages [pages]
  (let [res (atom [])]
    (doall
     (for [page pages]
       (swap! res (fn [old-res]
                    (conj old-res
                          (with-out-str
                            (print "\n")
                            (print (get-all-data-for (:text page)))
                            (print "\n")))))))
    @res))


#_(data-for-pages)


#_(get-all-refs-for "[[HYP]] - **I am guessing that the ability of arp2/3 complex to bind as frequently as it likes to actin filaments explains the discrepancy between CryoET and simulation measurements**")


(data-for-pages [
                 {:text
                  "[[EVD]] - siRNA silenced IRSp53 significantly reduced internalized 10kDa TMR Dextran, while siRNA silenced Swip1 did not in MDA-MB-231 cells. - [[@moreno-layseca2021cargospecific]]",
                  :text-uid "YEbfS-WDB",
                  :uid "YEbfS-WDB"}
                 {:text "[[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis."}])

