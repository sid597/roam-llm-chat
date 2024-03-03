(ns ui.actions.graph-overview-ai
  (:require
    [ui.utils :refer [p get-child-of-child-with-str-on-page pp ai-block-exists? chat-ui-with-context-struct default-chat-struct q create-new-block create-new-block-with-id get-todays-uid create-struct]]
    [ui.extract-data.graph-overview-ai :refer [get-explorer-pages]]))



(defn create-chat-ui-blocks-for-selected-overview [chat-block-uid context-block-uid]
  (let [pre               "*Load filtered pages into chat* : "
        {:keys
         [in-pages
          out-pages
          page-name]}     (get-explorer-pages)
        make-page         (fn [page]
                            (str "[[" page "]]"))
        pre-prompt        (str (-> (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" "Graph overview default pre prompt" "Pre prompt")
                                 :children
                                 first
                                 :string))
        context-structure (-> [{:s pre-prompt}
                               {:s (make-page page-name)}
                               {:s "**Incoming pages:** "}]
                            (concat (map #(do
                                            {:s  (make-page %)}) in-pages))
                            (concat
                              [{:s "**Outgoing pages:** "}]
                             (map #(do {:s  (make-page %)}) out-pages)))
        ai-block?          (ai-block-exists? (get-todays-uid))]

    (p (str pre "block with `AI chats` exist? " ai-block?))
    (if (some? ai-block?)
      (create-struct
        (default-chat-struct chat-block-uid context-block-uid nil context-structure)
        ai-block-exists?
        chat-block-uid
        true
        (p (str pre "Created a new chat block and opening in sidebar with context: " context-structure)))

      (create-struct
        (chat-ui-with-context-struct chat-block-uid context-block-uid context-structure)
        (get-todays-uid)
        chat-block-uid
        true
        (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar with context: " context-structure))))))

