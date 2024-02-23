(ns ui.actions.graph-overview-ai
  (:require
    [ui.utils :refer [p pp ai-block-exists? chat-ui-with-context-struct default-chat-struct q create-new-block create-new-block-with-id get-todays-uid create-struct]]
    [ui.extract-data.graph-overview-ai :refer [get-explorer-pages]]))



(defn create-chat-ui-blocks-for-selected-overview [chat-block-uid context-block-uid]
  (let [pre               "*Load filtered pages into chat*"
        {:keys
         [in-pages
          out-pages
          page-name]}     (get-explorer-pages)
        make-page         (fn [page]
                            (str "[[" page "]]"))
        context-structure (-> [{:s "**Write some instructions for context here**"}
                               {:s (make-page page-name)}
                               {:s "**Incoming pages:** "}]
                            (concat (map #(do
                                            {:s  (make-page %)}) in-pages))
                            (concat [{:s "**Outgoing pages:** "}]
                             (map #(do {:s  (make-page %)}) out-pages)))
        ai-block?          (ai-block-exists? (get-todays-uid))]

    (p (str pre "block with `AI chats` exist? " ai-block?))
    (if (some? ai-block?)
      (create-struct
        (default-chat-struct chat-block-uid context-block-uid nil context-structure)
        ai-block-exists?
        chat-block-uid
        true
        (do
          (p (str pre "Created a new chat block and opening in sidebar with context: "))))
          ;(pp context-structure)))
      (create-struct
        (chat-ui-with-context-struct chat-block-uid context-block-uid context-structure)
        (get-todays-uid)
        chat-block-uid
        true
        (do
          (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar with context: ")))))))
          ;(pp context-structure))))))
