(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [reagent.core :as r :refer [atom]]
            [ui.components.quick-buttons :refer [button-with-settings text-to-image-button discourse-graph-this-page-button]]
            [cljs-http.client :as http]
            [ui.components.get-context :refer [get-context-button get-suggestions-button]]
            [ui.components.search-pinecone :refer [search-pinecone]]
            [ui.extract-data.chat :refer [data-for-nodes get-all-images-for-node]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [buttons-settings chat-ui-with-context-struct ai-block-exists? button-popover button-with-tooltip model-mappings get-safety-settings update-block-string-for-block-with-child settings-button-popover image-to-text-for p get-child-of-child-with-str title->uid q block-has-child-with-str? call-llm-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))



(defn bottom-bar-gear []
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
      (doall
        (for [button ["Discourse graph this page" "Get context" "Get suggestions"]]
           ^{:key button}
           [buttons-settings button]))]]]])



(defn bottom-bar-buttons []
  (let [dgp-block-uid                (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
        dgp-discourse-graph-page-uid (:uid (get-child-with-str dgp-block-uid "Discourse graph this page"))
        dgp-default-model            (r/atom  (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Model"))
        dgp-default-temp             (r/atom   (js/parseFloat (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Temperature")))
        dgp-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Max tokens")))
        dgp-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Get linked refs"))
                                              true
                                              false))
        dgp-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Extract query pages"))
                                              true
                                              false))
        dgp-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str dgp-discourse-graph-page-uid "Settings" "Extract query pages ref?"))
                                              true
                                              false))
        dgp-active?                  (r/atom false)
        dgp-context                  (r/atom (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" "Discourse graph this page" "Context"))
        co-get-context-uid          (:uid (get-child-with-str
                                            (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                            "Get context"))
        co-default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str co-get-context-uid "Settings" "Temperature")))
        co-default-model            (r/atom (get-child-of-child-with-str co-get-context-uid "Settings" "Model"))
        co-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str co-get-context-uid "Settings" "Max tokens")))
        co-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Get linked refs"))
                                              true
                                              false))
        co-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Extract query pages"))
                                              true
                                              false))
        co-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Extract query pages ref?"))
                                              true
                                              false))
        co-pre-prompt                (get-child-of-child-with-str co-get-context-uid "Prompt" "Pre-prompt")
        co-remaining-prompt         (get-child-of-child-with-str co-get-context-uid "Prompt" "Further instructions")
        co-active?                  (r/atom false)
        sug-get-context-uid          (:uid (get-child-with-str
                                             (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                             "Get suggestions"))
        sug-default-model            (r/atom (get-child-of-child-with-str sug-get-context-uid "Settings" "Model"))
        sug-default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str sug-get-context-uid "Settings" "Temperature")))

        sug-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str sug-get-context-uid "Settings" "Max tokens")))
        sug-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Get linked refs"))
                                               true
                                               false))
        sug-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Extract query pages"))
                                               true
                                               false))
        sug-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Extract query pages ref?"))
                                               true
                                               false))
        sug-pre-prompt                (get-child-of-child-with-str sug-get-context-uid "Prompt" "Pre-prompt")
        sug-remaining-prompt         (get-child-of-child-with-str sug-get-context-uid "Prompt" "Further instructions")
        sug-active?                  (r/atom false)]
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
        false
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

       #_[:div {:style {:flex "1 1 1"}}
          [button-with-settings "Summarise this page"]]
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
                                       (create-struct
                                         (default-chat-struct chat-block-uid)
                                         ai-block?
                                         chat-block-uid
                                         true
                                         (p (str pre "Created a new chat block and opening in sidebar with context: " context)))
                                      (create-struct
                                        (chat-ui-with-context-struct chat-block-uid)
                                        open-page-uid
                                        chat-block-uid
                                        true
                                        (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar with context: " context)))))))}

          "Chat with this page"]]]
       #_[:> Divider]
       #_[:div
          {:style {:flex "1 1 1"}}
          [button-with-tooltip
           "Begin a brand new empty chat from any page (including zoomed-in pages/blocks), no context is included. Think of this as a quick chat. The chat block will be added in your daily notes page and the chat window will appear in your right sidebar.
        Choose your LLM and adjust its settings within the chat interface. ."
           [:> Button {:minimal true
                       :small true
                       :on-click (fn [e]
                                   (p "*Start chat in daily notes, show in sidebar* :button clicked")
                                   (let [pre            "*Start chat in daily notes, show in sidebar* :"
                                         chat-block-uid (gen-new-uid)
                                         ai-block?      (ai-block-exists? (get-todays-uid))]
                                     (p (str pre "block with `AI chats` exist? " ai-block?))
                                     (if (some? ai-block?)
                                       (create-struct
                                         (default-chat-struct chat-block-uid)
                                         ai-block?
                                         chat-block-uid
                                         true
                                         (p (str pre "Created a new chat block and opening in sidebar. With no context. ")))
                                       (create-struct
                                         (chat-ui-with-context-struct chat-block-uid)
                                         (get-todays-uid)
                                         chat-block-uid
                                         true
                                         (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar. With no context."))))))}
            "Start new chat"]]]
       #_[:> Divider]
       #_[:div
          {:style {:flex "1 1 1"}}
          [:> Button
           {:minimal true
            :small true
            :on-click (fn [e]
                        (let [url         "https://roam-llm-chat-falling-haze-86.fly.dev/get-openai-embeddings"
                              upsert-data  (clj->js {:input (subvec (all-dg-nodes) 1600)})
                              multiple-query-data   (clj->js {:input ["Myosin plays a critical role in assisting endocytosis under conditions of high membrane tension"
                                                                      #_"Increasing membrane tension from 2 pN/nm to 2000 pN/nm in simulations showed a broader assistance by myosin in internalization"]
                                                              :top-k "8"})
                              single-query-data (clj->js {:input ["Increasing membrane tension from 2 pN/nm to 2000 pN/nm in simulations showed a broader assistance by myosin in internalization
                             Resistance to internalization increased as myosin unbinding rate decreased at higher membrane tension in simulations
                             At 20 pN/nm membrane tension, areas with low myosin unbinding rates had decreased internalization resistance
                            Investigate the relationship between myosin catch bonding parameters and internalization efficiency in live cell experiments
                            Myosin assists more broadly in membrane internalization under higher tension conditions
                            High membrane tension facilitates myosin’s role in overcoming resistance to internalization  "]
                                                          :top-k "3"})



                              headers      {"Content-Type" "application/json"}
                              res-ch       (http/post url {:with-credentials? false
                                                           :headers headers
                                                           :json-params multiple-query-data})]
                          #_(println "SENDING EMBEDDINGS REQUEST" (count (all-dg-nodes))) ""
                          #_(println "DATA : " (take 2 upsert-data))
                          (println "query data" single-query-data)
                          (take! res-ch (fn [res]
                                          (let [embeddings (->> (js->clj (-> res :body ) :keywordize-keys true)
                                                             (map
                                                               (fn [x]
                                                                 (str (-> x :metadata :title) "- Score: " (:score x)))))]
                                            #_(println "GOT EMBEDDINGS :" "--" embeddings))))))}
           "Create embeddings"]]
       #_[:> Divider]
       #_[:div
          {:style {:flex "1 1 1"}}
          [button-with-tooltip
           "Same as `Start new chat` button but starts the chat in the block you are focused on."
           [:> Button {:minimal true
                       :small true
                       :style {:flex "1 1 1"}
                       :on-click (fn [e]
                                   (p "*Start chat in focused block* :button clicked")
                                   (let [pre            "*Start chat in focused block* "
                                         chat-block-uid (gen-new-uid)
                                         [parent-uid
                                          block-order]  (get-block-parent-with-order (get-focused-block))
                                         chat-struct    (chat-ui-with-context-struct chat-block-uid nil nil block-order)]
                                     (create-struct
                                       chat-struct
                                       parent-uid
                                       chat-block-uid
                                       false
                                       (p (str pre "Created a new chat block under focused block and opening in sidebar. With no context.")))))}
            "Start new chat in focused block"]]]
       #_[:> Divider]
       #_[:div
          {:style {:flex "1 1 1"}}
          [discourse-graph-this-page-button]]
       #_[:> Divider]
       #_[:div {:style {:flex "1 1 1"}}
          [filtered-pages-button]]
       #_[:> Divider]
       #_[:div
          {:style {:flex "1 1 1"}}
          [text-to-image-button]]])))

