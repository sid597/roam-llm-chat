(ns ui.actions.graph-overview-ai
  (:require
    [ui.utils :refer [q create-new-block create-new-block-with-id get-todays-uid create-struct]]
    [ui.extract-data.graph-overview-ai :refer [get-explorer-pages]]))



(defn create-chat-ui-blocks-for-selected-overview [chat-block-uid context-block-uid]
  (let [ai-block      (ffirst
                        (q '[:find ?uid
                             :in $ ?today
                             :where
                             [?e :block/uid ?today]
                             [?e :block/children ?c]
                             [?c :block/string "AI chats"]
                             [?c :block/uid ?uid]]
                          (get-todays-uid)))
        {:keys
         [in-pages
          out-pages
          page-name]} (get-explorer-pages)
        make-page         (fn [page]
                            (str "[[" page "]]"))
        context-structure(-> [{:s "**Write some instructions for context here**"}
                              {:s (make-page page-name)}
                              {:s "**Incoming pages:** "}]
                           (concat (map #(do
                                           {:s  (make-page %)}) in-pages))
                           (concat [{:s "**Outgoing pages:** "}]
                            (map #(do {:s  (make-page %)}) out-pages)))]
    (if (some? ai-block)
      (create-struct
        {:s "{{ chat-llm }}"
         :op false
         :u chat-block-uid
         :c [{:s "Messages"}
             {:s "Context"
              :c context-structure
              :u context-block-uid}]}
        ai-block
        chat-block-uid
        true)
      (create-struct
        {:s "AI chats"
         :c [{:s "{{ chat-llm }}"
              :op false
              :u chat-block-uid
              :c [{:s "Messages"}
                  {:s "Context"
                   :u context-block-uid
                   :c context-structure}]}]}
        (get-todays-uid)
        chat-block-uid
        true))))
