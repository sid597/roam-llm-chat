(ns ui.components.quick-buttons
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.extract-data.chat :as ed :refer [data-for-pages data-for-blocks]]
            [ui.components.chat :refer [chat-context chin]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [p get-child-of-child-with-str title->uid pp q block-with-str-on-page? call-openai-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))


(defn button-popover
  ([render-comp]
   (button-popover render-comp "#eeebeb"))
  ([render-comp bg-color]
   [:> Popover
    ;{:position "bottom"}
    [:> Button {:icon "cog"
                :minimal true
                :small true
                :style {:background-color bg-color}}]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))


(defn extract-context-children-data-as-str [context]
  (let [res (r/atom "")
        children (sort-by :order (:children @context))]
    (doseq [child children]
      (let [child-str (:string child)]
        ;(println "res " child-str)
        (swap! res str  (str child-str "\n"))))
    res))

(comment
  (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" "Summarise this page" "Context"))


(defn button-with-settings [button-name]
  (let [block-uid (block-with-str-on-page? (title->uid "LLM chat settings") "Quick action buttons")
        get-linked-refs? (r/atom (if (= "true" (get-child-of-child-with-str block-uid "Settings" "Get linked refs"))
                                   true
                                   false))
        active? (r/atom false)
        default-msg-value (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Max tokens")))
        default-temp (r/atom (js/parseFloat (get-child-of-child-with-str block-uid "Settings" "Temperature")))
        default-model (r/atom (get-child-of-child-with-str block-uid "Settings" "Model"))
        context (r/atom (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" button-name "Context"))]

    (fn [_]
      #_(println "--" (get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" button-name "Context"))
      [:> ButtonGroup
       {:class-name "button-with-settings"
        :style {:overflow "hidden"
                :display "flex"
                :flex-direction "row"
                :justify-content "space-between"
                :align-items "center"
                :flex "1 1 1"}
        :minimal true}
       [:div {:style {:flex "1 1 1"}}
        [button-popover
         [:> Card {:elevation 3
                   :style {:flex "1"
                           :margin "0"
                           :display "flex"
                           :flex-direction "column"
                           :border "2px solid rgba(0, 0, 0, 0.2)"
                           :border-radius "8px"}}
          [:div.chat-input-container
           {:style {:display "flex"
                    :flex-direction "row"
                    :border-radius "8px"
                    :margin "10px 10px -10px 10px  "
                    :background-color "whitesmoke"
                    :border "1px"}}
           [chat-context context #()]]
          [chin default-model default-msg-value default-temp get-linked-refs? active? block-uid]]]]
       [:div {:style {:flex "1 1 1"}}
         [:> Button {:minimal true
                     :small true
                     :loading @active?
                     :on-click (fn [e]
                                 (p "*Summarise this page* :button clicked")
                                 (when (not @active?)
                                   (reset! active? true)
                                  (go
                                   (let [pre               "*Summarise this page* :"
                                         current-page-uid    (<p! (get-open-page-uid))
                                         title               (uid->title current-page-uid)
                                         block-data          (when (nil? title)
                                                               (str
                                                                 "```"
                                                                 (clojure.string/join "\n -----" (data-for-blocks [current-page-uid]))
                                                                 "```"))
                                         already-summarised? (block-with-str-on-page? current-page-uid "AI summary")
                                         parent-block-uid    (gen-new-uid)
                                         res-block-uid       (gen-new-uid)
                                         struct              (if (nil? already-summarised?)
                                                               {:s "AI summary"
                                                                :u parent-block-uid
                                                                :c [{:s ""
                                                                     :u res-block-uid}]}
                                                               {:s ""
                                                                :u res-block-uid})
                                         top-parent          (if (nil? already-summarised?)
                                                               current-page-uid
                                                               already-summarised?)
                                         context             (extract-context-children-data-as-str
                                                               (r/atom (get-child-of-child-with-str-on-page
                                                                         "LLM chat settings" "Quick action buttons" button-name "Context")))
                                         page-data           (when-not (nil? title) (data-for-pages [{:text (str title)}] get-linked-refs?))
                                         send-data           (if (nil? title)
                                                                 (str @context "\n" block-data)
                                                                 (str @context "\n" page-data))
                                         settings            {:model @default-model
                                                              :max-tokens @default-msg-value
                                                              :temperature @default-temp}
                                         messages            [{:role "user"
                                                               :content send-data}]]
                                     (do
                                       (create-struct struct top-parent res-block-uid false
                                         (p (str pre "Created a new `AI summary` block with uid: " res-block-uid " and parent uid: " parent-block-uid "and with context: ")))

                                       (<p! (js/Promise.
                                              (fn [_]
                                                (p (str pre "Calling openai api, with settings : " settings))
                                                (p (str pre "and messages : " messages))
                                                (p (str pre "Now sending message and wait for response ....."))
                                                (call-openai-api
                                                  {:messages messages
                                                   :settings settings
                                                   :callback (fn [response]
                                                               (p (str pre "openai api response received: " response))
                                                               (let [res-str (-> response
                                                                               :body)]
                                                                 (update-block-string
                                                                   res-block-uid
                                                                   (str res-str)
                                                                   (js/setTimeout
                                                                     (fn []
                                                                       (p (str pre "Updated block " res-block-uid " with response from openai api"))
                                                                       (reset! active? false))
                                                                     500))))})))))))))}

                    "Summarise this page"]]])))

