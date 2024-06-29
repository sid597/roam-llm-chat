(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.quick-buttons :refer [button-with-settings text-to-image-button discourse-graph-this-page-button]]
            [cljs-http.client :as http]
            [ui.extract-data.chat :refer [data-for-nodes get-all-images-for-node]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [p call-llm-api button-with-tooltip count-tokens-api all-dg-nodes image-to-text-for ai-block-exists? chat-ui-with-context-struct uid->title log get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
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

        "Chat with this page"]]]
     [:> Divider]
     [:div
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
     [:div
      {:style {:flex "1 1 1"}}
      [:> Button
       {:minimal true
        :small true
        :on-click  (fn [e]
                     (let [initial-data (take 695 (all-dg-nodes)) ;; 40k tokens
                           new-title    "[[QUE]] - What is the branching angle of arp23 complex?"
                           prompt       (str
                                          "<system_context>
                                          You are an AI assistant trained to analyze discourse relationships in scientific research data, specifically in the field of cellular biology focusing on the actin cytoskeleton and endocytosis.
                                          </system_context>

                                          <lab_context>
                                          Our lab uses Roam Research for knowledge organization with the following structure:
                                          <node_types>
                                          - Question (QUE)
                                          - Claim (CLM)
                                          - Evidence (EVD)
                                          </node_types>
                                          <edge_types>
                                          - Informs
                                          - Supports
                                          - Opposes
                                          </edge_types>
                                          <discourse_relationships>
                                          - (Evidence, Informs, Question)
                                          - (Question, InformedBy, Evidence)
                                          - (Evidence, Supports, Claim)
                                          - (Claim, SupportedBy, Evidence)
                                          - (Evidence, Opposes, Claim)
                                          - (Claim, OpposedBy, Evidence)
                                          </discourse_relationships>
                                          </lab_context>

                                          <task>
                                          Analyze the provided <initial_data> to find discourse relationships relevant to the given <new_title>. Use your expertise to identify meaningful connections.
                                          </task>
                                          <input_format>
                                          <initial_data_format>
                                          A list of maps, each containing:
                                          - uid: Unique identifier of the node
                                          - title: Title of the node
                                          </initial_data_format>
                                          <initial-data>"
                                          initial-data
                                          "</initial-data>
                                          <new_title>"
                                          new-title
                                          "</new_title>
                                          </input_format>
                                          <output_format>
                                          Return a list of maps, each containing:
                                          - uid: Unique identifier of the related node
                                          - title: Title of the related node
                                          - relationship: Type of discourse relationship (Informs, Supports, or Opposes)
                                          </output_format>

                                          <instructions>
                                          1. Examine the <new_title> and understand its implications within the context of actin cytoskeleton and endocytosis research.
                                          2. Review the <initial_data> and identify nodes that could form valid discourse relationships with the <new_title>.
                                          3. For each relevant node, determine the appropriate relationship type based on the content and research context.
                                          4. Only include relationships that strictly adhere to the defined <discourse_relationships>.
                                          5. If no valid relationships are found, return an empty list rather than forcing irrelevant connections.
                                          </instructions>

                                           <quality_guideline>
                                           Prioritize accuracy and relevance over quantity. It's better to return fewer, highly relevant relationships than many tenuous connections. Ensure all relationships logically fit within the context of actin cytoskeleton and endocytosis research.
                                           </quality_guideline>")



                           #_(str
                               "<Context>
                                          Our lab uses Roam Research to organize our collaboration and knowledge sharing related to understanding endocytosis in cells.
                                          We capture questions (QUE), claim (CLM), evidence (EVD), source (represented as `@whatever-the-source`)
                                          on separate pages in Roam. Each page has a title summarizing the key insight. We call these discourse nodes.
                                          <node-type> QUE, CLM, EVD </node-type>
                                          These discourse nodes also have edges between themselves, <edge-type> Informs, Supports, Opposes </edge-type>
                                          The triple of directed branch(source-node, relation, target-node) is called discourse-relationships.
                                          <discourse-relationships>
                                          (Evidence, Informs, Question)
                                          (Evidence, Supports, Claim)
                                          (Evidence, Opposes, Claim)
                                          </discourse-relationships>
                                          Each of the tuple above represents a discourse relationship.
                                          </Context>

                                          <Your-personality>
                                          You are a researcher at my bio lab where We combine mathematical modeling, human stem cell genome-editing, and fluorescence microscopy to study how the actin cytoskeleton produces force to function in cellular membrane bending and trafficking processes. Our research focuses on the mechanical relationship between the actin cytoskeleton and mammalian endocytosis. We aim to identify mechanisms by which emergent architectures of cytoskeletal networks arise based on the initial positions and geometries of endocytic actin-binding proteins. We also study the mechanisms by which the cytoskeleton actively adapts to changing loads to ensure the timely completion of endocytosis.
                                          </Your-personality>
                                          <Your-job>
                                            Given the Initial-data, Response-format and new-title below. For the new-title you have, use your high level reasoning ability
                                            like my lab researcher to find if there are nodes in the Initial-data that might form a discourse relationships. If there are none, please don't return any just for the sake of it.
                                            Respond with matching titles as the Response-format says.
                                          </Your-job>

                                          <NOTE> 1. No other type of discourse relationship is possible than the ones provided above.
                                                 2. If you don't find any sensible discourse-relationships that STRICTLY follow the type of discourse-relationship mentioned above then please don't include them. </NOTE>\\n\n
                                          <Initial-data-format>
                                          Its a list of maps, each map is of the format:
                                          {:uid  uid-of-some-node
                                           :title title-of-the-node}
                                          </Initial-data-format>
                                          <Initial-data>\n"
                               initial-data
                               "</Initial-data>
                                          <Response-format>
                                          The data should be in this format:
                                          [{:uid \"\" :title \"\" :relationship \"\"}
                                           {:uid \"\" :title \"\" :relationship \"\"}
                                           ...]
                                           </Response-format>
                                          <New-title> "
                               new-title
                               " </New-title> \n")

                           tools  [{:name "return_similar_titles"
                                    :description "Return an array of titles similar to the one provided in the prompt. Reply with uid, title, and relationship fields"
                                    :input_schema
                                    {:type "object"
                                     :properties
                                     {:items
                                      {:type "array"
                                       :items
                                       {:type "object"
                                        :properties
                                        {:uid {:type "string"
                                               :description "This is unique id for the discourse-node"}
                                         :title {:type "string"
                                                 :description "This is the title of the discourse-node"}
                                         :relationship {:type "string"
                                                        :enum ["Supports" "Informs" "Opposes"]
                                                        :description "Type of discourse relationship with the discourse-node"}}
                                        :required ["uid" "title" "relationship"]}}}
                                     :required ["items"]}}]
                           #_[{:name "analyze_discourse_relationships"
                               :description "Analyze the Initial-data to find discourse relationships that match the new-title. Return relevant nodes with their relationships."
                               :input_schema
                               {:type "object"
                                :properties
                                {:items
                                 {:type "array"
                                  :items
                                  {:type "object"
                                   :properties
                                   {:uid {:type "string"
                                          :description "Unique id for the discourse node"}
                                    :title {:type "string"
                                            :description "Title of the discourse node"}
                                    :relationship {:type "string"
                                                   :enum ["Supports" "Informs" "Opposes"]
                                                   :description "Type of discourse relationship with the new title"}}
                                   :required ["uid" "title" "relationship"]}}}
                                :required ["items"]}}]]
                       (println "prompt" prompt)
                       (call-llm-api
                         {:messages [{:role "user"
                                      :content prompt}]
                          :settings {:model #_"claude-3-5-sonnet-20240620"
                                     "claude-3-haiku-20240307"
                                     :temperature 0.1
                                     :max-tokens 500
                                     :tools tools
                                     :tool_choice {:type "tool"
                                                   :name "return_similar_titles"}}
                          :callback (fn [response]
                                      (println "----- Got response from llm -----")
                                      (cljs.pprint/pprint (-> response :body))
                                      (println "---------- END ---------------"))})))


        #_(fn [e]
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
     [:> Divider]
     [:div
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


