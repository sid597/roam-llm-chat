(ns ui.actions.graph-overview-ai
  (:require
    [ui.utils :refer [ai-block-exists? chat-ui-with-context-struct default-chat-struct q create-new-block create-new-block-with-id get-todays-uid create-struct]]
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
    (if (some? (ai-block-exists? (get-todays-uid)))
      (create-struct
        (default-chat-struct chat-block-uid context-block-uid nil context-structure)
        ai-block-exists?
        chat-block-uid
        true)
      (create-struct
        (chat-ui-with-context-struct chat-block-uid context-block-uid context-structure)
        (get-todays-uid)
        chat-block-uid
        true))))
