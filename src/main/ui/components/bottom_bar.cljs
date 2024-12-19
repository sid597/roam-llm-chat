(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [reagent.core :as r :refer [atom]]
            [ui.components.quick-buttons :refer [ discourse-graph-this-page-button]]
            [cljs-http.client :as http]
            [ui.components.get-context :refer [get-context-button get-suggestions-button]]
            [ui.components.search-pinecone :refer [search-pinecone]]
            [ui.extract-data.chat :refer [data-for-nodes get-all-images-for-node]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [pull-deep-block-data extract-data buttons-settings chat-ui-with-context-struct ai-block-exists? button-popover button-with-tooltip model-mappings get-safety-settings update-block-string-for-block-with-child settings-button-popover image-to-text-for p get-child-of-child-with-str title->uid q block-has-child-with-str? call-llm-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))




(defn bottom-bar-buttons []
  (js/console.time "bottom bar setup")
  (let [dgp-block-uid                (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
        dgp-discourse-graph-page-uid (:uid (get-child-with-str dgp-block-uid "Discourse graph this page"))
        dgp-data                     (-> dgp-discourse-graph-page-uid
                                       (pull-deep-block-data)
                                       extract-data)
        dgp-default-model            (r/atom (:model dgp-data))
        dgp-default-temp             (r/atom (:temperature dgp-data))
        dgp-default-max-tokens       (r/atom (:max-tokens dgp-data))
        dgp-get-linked-refs?         (r/atom (:get-linked-refs? dgp-data))
        dgp-extract-query-pages?     (r/atom (:extract-query-pages? dgp-data))
        dgp-extract-query-pages-ref? (r/atom (:extract-query-pages-ref? dgp-data))
        dgp-active?                  (r/atom (:active? dgp-data))
        dgp-context                  (r/atom (:context dgp-data))



        co-get-context-uid           (:uid (get-child-with-str
                                             (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                             "Get context"))
        co-get-context-data         (-> co-get-context-uid
                                      (pull-deep-block-data)
                                      extract-data)
        co-default-temp             (r/atom (:temperature co-get-context-data))
        co-default-model            (r/atom (:model co-get-context-data))
        co-default-max-tokens       (r/atom (:max-tokens co-get-context-data))
        co-get-linked-refs?         (r/atom (:get-linked-refs? co-get-context-data))
        co-extract-query-pages?     (r/atom (:extract-query-pages? co-get-context-data))
        co-extract-query-pages-ref? (r/atom (:extract-query-pages-ref? co-get-context-data))
        co-pre-prompt               (:pre-prompt co-get-context-data)
        co-remaining-prompt         (:further-instructions co-get-context-data)
        co-active?                  (r/atom (:active? co-get-context-data))


        sug-get-context-uid          (:uid (get-child-with-str
                                             (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                             "Get suggestions"))
        sug-get-context-data         (-> sug-get-context-uid
                                       (pull-deep-block-data)
                                       extract-data)
        sug-default-model            (r/atom (:model sug-get-context-data))
        sug-default-temp             (r/atom (:temperature sug-get-context-data))
        sug-default-max-tokens       (r/atom (:max-tokens sug-get-context-data))
        sug-get-linked-refs?         (r/atom (:get-linked-refs? sug-get-context-data))
        sug-extract-query-pages?     (r/atom (:extract-query-pages? sug-get-context-data))
        sug-extract-query-pages-ref? (r/atom (:extract-query-pages-ref? sug-get-context-data))
        sug-pre-prompt               (:pre-prompt sug-get-context-data)
        sug-remaining-prompt         (:further-instructions sug-get-context-data)
        sug-active?                  (r/atom (:active? sug-get-context-data))]
    (fn []
      (p "Render bottom bar buttons")
      [:> ButtonGroup
       {:style {:display         "flex"
                :justify-content "center"
                :align-items     "center"
                :width           "100%"}
        :fill true}
       [:> ButtonGroup
        {:class-name "button-with-settings"
         :style {:overflow "hidden"
                 :display "flex"
                 :flex-direction "row"
                 :justify-content "space-between"
                 :align-items "center"
                 :flex "1 1 1"}
         :minimal true}
        [:div {:class-name "Classes.POPOVER_DISMISS_OVERRIDE"
               :style {:flex "1 1 1"}}
         [:> Popover
               [:> Button {:icon "cog"
                               :minimal true
                               :small true
                               :style {:background-color "#eeebeb"}}]
          [:> Menu
           {:style {:padding "20px"}
            :class-name "Classes.POPOVER_DISMISS_OVERRIDE"}
           [buttons-settings
            "Discourse graph this page"
            dgp-discourse-graph-page-uid
            dgp-default-temp
            dgp-default-model
            dgp-default-max-tokens
            dgp-get-linked-refs?
            dgp-extract-query-pages?
            dgp-extract-query-pages-ref?]

           [buttons-settings
             "Get Context"
             co-get-context-uid
             co-default-temp
             co-default-model
             co-default-max-tokens
             co-get-linked-refs?
             co-extract-query-pages?
             co-extract-query-pages-ref?]
           [buttons-settings
            "Get Suggestions"
            sug-get-context-uid
            sug-default-temp
            sug-default-model
            sug-default-max-tokens
            sug-get-linked-refs?
            sug-extract-query-pages?
            sug-extract-query-pages-ref?]]]]]


       [discourse-graph-this-page-button
        dgp-block-uid
        dgp-default-model
        dgp-default-temp
        dgp-default-max-tokens
        dgp-get-linked-refs?
        dgp-extract-query-pages?
        dgp-extract-query-pages-ref?
        dgp-active?
        dgp-context]


       [:> Divider]
       [get-context-button
        nil
        co-default-model
        co-default-temp
        co-default-max-tokens
        co-get-linked-refs?
        co-extract-query-pages?
        co-extract-query-pages-ref?
        co-active?
        co-pre-prompt
        co-remaining-prompt]

       [:> Divider]
       [:div.search-pinecone
        {:style {:flex "0 0 50%"}}
        [button-with-tooltip
         "Do semantic search over existing Discourse graph nodes. Type your query and press the send button.
        We get top 3 results, select any one to go to that query result page. "
         [search-pinecone]]]
       [:> Divider]


       [get-suggestions-button
        nil
        sug-default-model
        sug-default-temp
        sug-default-max-tokens
        sug-get-linked-refs?
        sug-extract-query-pages?
        sug-extract-query-pages-ref?
        sug-active?
        sug-pre-prompt
        sug-remaining-prompt]

       [:> Divider]
       [:div
        {:style {:flex "1 1 1"}}
        [button-with-tooltip
         "Using the content of the current page (including zoomed-in pages/blocks) as context, start a conversation with your selected LLM.
        Control the LLM model, response length, and temperature in the interface that is created by this button."
         [:> Button {:minimal true
                     :small true
                     :style {:flex "1 1 1"}
                     :on-click (fn [e]
                                 ;; UPDATE THIS CODE
                                 (p "*Chat with this page* :button clicked")
                                 (go
                                   (let [pre            "*Chat with this page* :"
                                         chat-block-uid (gen-new-uid)
                                         open-page-uid (<p! (get-open-page-uid))
                                         page-title    (uid->title open-page-uid)
                                         context       (if (nil? page-title)
                                                         (str "((" open-page-uid "))")
                                                         (str "[[" page-title "]]"))
                                         context-struct [{:s context}
                                                         {:s ""}]
                                         ai-block? (ai-block-exists? open-page-uid)]
                                     (p (str pre "block with `AI chats` exist? " ai-block?))
                                     (p (str pre "context" context))
                                     ;(println "open page uid" open-page-uid)
                                     ;(println "page title" page-title)
                                     ;(println "extract block" block-data)
                                     (if (some? ai-block?)
                                       (do
                                        (js/console.time "Chat with this page")
                                        (create-struct
                                          (default-chat-struct chat-block-uid  nil nil context-struct)
                                          ai-block?
                                          chat-block-uid
                                          true
                                          (p (str pre "Created a new chat block and opening in sidebar with context: " context)))
                                        (js/console.timeEnd "Chat with this page"))
                                      (do
                                        (js/console.time "Chat with this page")
                                        (create-struct
                                          (chat-ui-with-context-struct chat-block-uid nil context-struct)
                                          open-page-uid
                                          chat-block-uid
                                          true
                                          (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar with context: " context)))
                                        (js/console.timeEnd "Chat with this page"))))))}
          "Chat with this page"]]]
       (js/console.timeEnd "bottom bar setup")])))
