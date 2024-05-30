(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.quick-buttons :refer [button-with-settings text-to-image-button discourse-graph-this-page-button]]
            [cljs-http.client :as http]
            [ui.extract-data.chat :refer [data-for-nodes get-all-images-for-node]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [p all-dg-nodes image-to-text-for ai-block-exists? chat-ui-with-context-struct uid->title log get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))




(defn bottom-bar-buttons []
  (p "Creating bottom bar buttons")
  (fn []
    (p "Render bottom bar buttons")
    [:> ButtonGroup
     [:> Divider]
     [:div {:style {:flex "1 1 1"}}
      [button-with-settings "Summarise this page"]]
     [:> Divider]
     [:div
      {:style {:flex "1 1 1"}}
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
                                      (default-chat-struct chat-block-uid nil nil context-struct)
                                      ai-block?
                                      chat-block-uid
                                      true
                                      (p (str pre "Created a new chat block and opening in sidebar with context: " context)))
                                   (create-struct
                                     (chat-ui-with-context-struct chat-block-uid nil context-struct)
                                     open-page-uid
                                     chat-block-uid
                                     true
                                     (p (str pre "Created a new chat block under `AI chats` block and opening in sidebar with context: " context)))))))}

       "Chat with this page"]]
     [:> Divider]
     [:div
      {:style {:flex "1 1 1"}}
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
       "Start chat in daily notes, show in sidebar"]]
     [:> Divider]
     #_[:div
        {:style {:flex "1 1 1"}}
        [:> Button
         {:minimal true
          :small true
          :on-click (fn [e]
                      (let [url          "http://localhost:3000/get-openai-embeddings"
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
                                          (cljs.pprint/pprint embeddings)
                                          (cljs.pprint/pprint res)
                                          #_(println "GOT EMBEDDINGS :" "--" embeddings))))))}
         "Create embeddings"]]
     [:> Divider]
     [:div
      {:style {:flex "1 1 1"}}
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
       "Start chat in focused block"]]
     [:> Divider]
     [:div
      {:style {:flex "1 1 1"}}
      [discourse-graph-this-page-button]]
     [:> Divider]
     [:div {:style {:flex "1 1 1"}}
      [filtered-pages-button]]
     [:> Divider]
     [:div
      {:style {:flex "1 1 1"}}
      [text-to-image-button]]]))

