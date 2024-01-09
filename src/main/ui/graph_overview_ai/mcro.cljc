(ns ui.graph-overview-ai.mcro)


(defmacro generate-query-pattern [depth]
  (let [queries (for [d (range 1 (+ depth 1))]
                  (let [base-index (* 2 d)]
                    (list 'list
                      `(~(symbol (str "?d" base-index)) :block/refs ~(symbol (str "?e" (dec base-index))))
                      `(~(symbol (str "?d" base-index)) :block/parents ~(symbol (str "?r" base-index)))
                      `(~(symbol (str "?r" base-index)) :node/title ~(symbol (str "?title" base-index))))))]
    (list 'concat
      (list 'list '[?e :node/title ?page])
      (apply #'concat queries))))
#_(defmacro generate-query-pattern [depth]
    (let [queries (for [d (range 1 (+ depth 1))]
                    (let [d-var (gensym "?d")
                          r-var (gensym "?r")
                          e-var (gensym "?e")
                          title-var (gensym "?title")]
                      (list 'list
                        `(~d-var :block/refs ~e-var)
                        `(~d-var :block/parents ~r-var)
                        `(~r-var :node/title ~title-var))))
          page-var (gensym "?page")
          e0-var (gensym "?e")]
      (println 'queries)
      (list 'concat
        (list 'list `[~e0-var :node/title ~page-var])
        (apply #'concat queries))))

#_(defmacro code-critic
    "Phrases are courtesy Hermes Conrad from Futurama"
    [bad good]
    `(do (println "Great squid of Madrid, this is bad code:"
           (quote ~bad))
         (println "Sweet gorilla of Manila, this is good code:"
           (quote ~good))))



(defmacro infix
  "Use this macro when you pine for the notation of your childhood"
  [infixed]
  (list (second infixed) (first infixed) (last infixed)))

(defmacro add
  [a b]
  `(+ ~a ~b))