(ns clockwork-ui.views
  (:require [reagent.core :as r]
            [cljs.nodejs :as nodejs]
            [clojure.string :as s]
            [cljs-time.core :as time]
            [cljs-time.coerce :as ft]
            [clockwork-ui.utils :as u]
            [re-frame.core :refer [subscribe dispatch]]
            [cljs.pprint :refer [pprint]]
            ))

(def Electron (nodejs/require "electron"))
(def remote (.-remote Electron))
(def dialog (.-dialog remote))

;; --- Common components ---

(defn text-input [form attribute]
  [:div.form-group
   [:label.col-sm-2.control-label
    (s/capitalize (name attribute))]
   [:div.col-sm-10
    [:input.form-control
     {:type "text"
      :value (get @form attribute)
      :on-change #(swap! form assoc attribute (u/event-val %))}]]])

(defn text-area [form attribute]
  [:textarea.form-control
   {:value (get @form attribute)
    :on-change #(swap! form assoc attribute (u/event-val %))}])

(defn time-input [form attribute]
  (let [val (u/format-time (get @form attribute))
        on-change #(swap! form assoc attribute
                          (u/parse-duration-string (u/event-val %)))]
    [:input.form-control
     {:type "text"
      :placeholder val
      :on-change on-change}]))

(defn debug-db []
  (let [db (subscribe [:db])]
    (fn []
      [:div.row.debug-db
       [:pre
        [:code.clojure
         (with-out-str (pprint @db))]]])))

;; --- Navigation ---

(defn navigation []
  (fn [panel]
  [:nav {:class "navbar navbar-default navbar-fixed-top"}
   [:div {:class "container"}
    [:div {:class "navbar-header"}
     [:button {:type "button" :class "navbar-toggle collapsed" :data-toggle "collapse"
               :data-target "#navbar" :aria-expanded "false" :aria-controls "navbar"}
      [:span {:class "sr-only"} "Toggle navigation"]
      [:span {:class "icon-bar"}]
      [:span {:class "icon-bar"}]
      [:span {:class "icon-bar"}]]

     [:a {:class "navbar-brand" :href "#"} "ClockWork"]]
    [:div {:id "navbar" :class "navbar-collapse collapse"}
     [:ul {:class "nav navbar-nav"}
      [:li {:class (if (= panel "timeslips") "active")}
       [:a {:class "item" :href "#"
            :on-click #(dispatch [:goto-timeslips])} "My Work"]]
      [:li {:class (if (= panel "projects") "active")}
       [:a {:href "#"
            :on-click #(dispatch [:goto-projects]) } "Projects"]]
      #_ [:li {:class "disabled"}
          [:a {:href "#"} "Reports"]]
      #_ [:li {:class "disabled"}
          [:a {:href "#"} "Manage"]]]
     [:ul {:class "nav navbar-nav navbar-right"}
      #_ [:li {:class "disabled"}
       [:a {:href "#"} "Settings"]]]]]]))

;; --- Timeslips view components ---

(defn timeslip-duration []
  (fn [timeslip]
    (let [duration (u/timeslip-seconds timeslip nil)]
      [:p.lead
      [:strong
       (u/format-time duration true)]])))

(defn timeslip-clock []
  (let [clock (subscribe [:clock])]
    (fn [timeslip]
      (let [duration (u/timeslip-seconds timeslip @clock)]
        [:h2
          (u/format-time duration true)]))))

(defn timeslip-clock-edit []
  (let [clock (subscribe [:clock])]
    (fn [timeslip]
      (let [duration (u/timeslip-seconds @timeslip @clock)
            _ (if-not (:duration @timeslip)
                (swap! timeslip assoc :duration duration))]
        (time-input timeslip :duration)))))

(defn new-timeslip-form []
  (let [val (r/atom "")
        new-timeslip (fn [] {:description @val})
        change #(do
                  (reset! val (u/event-val %)))
        save #(let [timeslip (new-timeslip)]
                (.preventDefault %)
                (reset! val "")
                (dispatch [:add-timeslip timeslip]))]
    (fn []
        [:form
         [:div.container
          [:div.row
           [:div.col-xs-12.col-sm-10
            [:input.form-control.input-lg
             {:id "new-timeslip-description"
              :type "text"
              :placeholder "What are you working on?"
              ;; :autofocus true
              :value @val
              :on-change change
              :on-key-down #(case (.-which %)
                              13 (save %)
                              nil)}]]
           [:div.col-xs-12.col-sm-2
            [:button.btn.btn-success.btn-lg.col-xs-12.col-sm-12.navbar-right
             {:type "button" :on-click save}
             [:span {:class (str "glyphicon glyphicon-play")
                     :aria-hidden "true"}]
             [:span {:class "sr-only"} "Start"]]]]]])))

(defn show-running-timeslip [{:keys [id project client task description] :as timeslip}]
  (let [project-html (if-not (s/blank? project) [:strong project])
        client-html (if-not (s/blank? client) (str " (" client ")"))
        task-html (if-not (s/blank? task) (str task " - "))]
    [:div.running-timeslip
     [:div.col-xs-6.col-sm-7
      [:p.lead
       project-html
       client-html]
      [:p task-html [:em description]]]
     [:div.col-xs-6.col-sm-3.text-right
      [timeslip-clock timeslip]]
     [:div.col-xs-12.col-sm-2
      [:button.btn.btn-danger.navbar-btn.btn-lg.col-xs-12.col-sm-12
       {:type "button" :on-click #(dispatch [:stop-timeslip id])}
       [:span {:class (str "glyphicon glyphicon-stop")
               :aria-hidden "true"}]
       [:span {:class "sr-only"} "Stop"]]]]))

(defn running-timer []
  (let [running-timeslip (subscribe [:running-timeslip])]
    [:div.row
     [:div.col-sm-12
      (if @running-timeslip
        [show-running-timeslip @running-timeslip]
        [new-timeslip-form])]]))

(defn head-row []
  (let [date (subscribe [:active-day])]
    (fn []
      (let [weekday (nth u/weekdays (- (time/day-of-week @date) 1))
            month-number (- (time/month @date) 1)
            month (nth u/months month-number)
            day (time/day @date)]
        [:div
         [:div.col-xs-6.col-sm-6
          [:h2 (str weekday " ")
           [:small (str day " " month)]]]
         [:div.col-xs-6.col-sm-6.text-right
          [:div.btn-group.navbar-btn {:role "group"}
           [:button.btn.btn-default.navbar-btn
            {:type "button"
             :on-click #(dispatch [:goto-previous-day])}
            [:span.glyphicon.glyphicon-backward {:aria-hidden true}]]
           [:button.btn.btn-default.navbar-btn
            {:type "button"
             :on-click #(dispatch [:goto-today])}
            [:span "Today"]]
           [:button.btn.btn-default.navbar-btn
            {:type "button"
             :on-click #(dispatch [:goto-next-day])}
            [:span.glyphicon.glyphicon-forward {:aria-hidden true}]]]]]))))

(defn remove-warning-dialog [id]
  (let [options {:type "question"
                 :buttons ["Cancel" "Remove"]
                 :defaultId 0
                 :title "Confirm Timeslip Deletion"
                 :message "Delete this timeslip?"
                 :detail "Are you sure? This action cannot be undone"
                 }
        action (.showMessageBox dialog (clj->js options))]
    (if (= action 1)
      (dispatch [:remove-timeslip id]))))

(defn timeslip-edit []
  (let [new-timeslip (r/atom {})]
    (fn [timeslip editing]
      (let [{:keys [id stopped-at]} timeslip
            _ (if (empty? @new-timeslip)
                (reset! new-timeslip timeslip))]
        [:tr {:id (str "timeslip-" id)}
         [:td
          [:form.form-horizontal
           [text-input new-timeslip :client]
           [text-input new-timeslip :project]
           [text-input new-timeslip :task]
           [text-area new-timeslip :description]]]
         [:td
          [:div.col-md-8.col-lg-8.col-sm-12
           [:div.col-xs-12.col-sm-12.col-md-8.col-lg-10
            [timeslip-clock-edit new-timeslip]]
           [:button.btn.btn-default.col-xs-12.col-sm-12.col-md-4.col-lg-2
            {:type "button"
             :on-click #(dispatch [:add-timeslip (dissoc timeslip :stopped-at)])}
            [:span {:class (str "glyphicon glyphicon-play")
                    :aria-hidden "true"}]
            [:span {:class "sr-only"} "Start"]]]
          [:div.col-xs-12.col-sm.12.col-md-4.col-lg-4.col-sm-12
           [:div
            [:button.btn.btn-default.col-xs-12
             {:on-click #(do
                           (dispatch [:update-timeslip @new-timeslip])
                           (reset! editing false))}
             [:span.glyphicon.glyphicon-save
              {:aria-hidden "true"}]
             [:span {:class "sr-only"} "Save Timeslip"]]
            [:button.btn.btn-danger.col-xs-12
             {:on-click #(remove-warning-dialog id)}
             [:span.glyphicon.glyphicon-remove
              {:aria-hidden "true"}]
             [:span {:class "sr-only"} "Remove Timeslip"]]]]]]))))

(defn timeslip-show [timeslip editing]
  (let [{:keys [id project client task description stopped-at]} timeslip
        project-html (if-not (s/blank? project) [:strong project])
        client-html (if-not (s/blank? client) (str " (" client ")"))
        task-html (if-not (s/blank? task) (str task " - ")) ]
    [:tr {:id (str "timeslip-" (:id timeslip))}
     [:td
      [:div.timeslip
       [:p.lead
        project-html
        client-html]
       [:p task-html [:em description]]]]
     [:td
      [:div.col-md-8.col-lg-8.col-sm-12
       [:div.col-sm-8.col-xs-12.col-lg-8
        [timeslip-duration timeslip]]
       [:button.btn.btn-default.col-sm-4.col-lg-4.col-xs-12
        {:type "button"
         :on-click #(dispatch [:add-timeslip (dissoc timeslip :stopped-at)])}
        [:span {:class (str "glyphicon glyphicon-play")
                :aria-hidden "true"}]
        [:span {:class "sr-only"} "Start"]]]
      [:div.col-md-4.col-lg-4.col-sm-12.col-xs-12
       [:button.btn.btn-default.col-md-12.col-sm-12.col-xs-12
        {:on-click #(reset! editing true)}
        [:span.glyphicon.glyphicon-pencil
         {:aria-hidden "true"}]
        [:span {:class "sr-only"} "Edit Timeslip"]]]]]))

(defn timeslip-row []
  (let [editing (r/atom false)]
    (fn [timeslip]
       (if @editing
         ;; timeslip edit
         [timeslip-edit timeslip editing]
         ;; timeslip show
         [timeslip-show timeslip editing]))))

(defn timeslips-table []
  (let [timeslips (subscribe [:active-day-timeslips])]
    (fn []
      (let [label-task ""
            label-time ""]
        [:div.row
         [:table.table.table-hover
          [:thead
           [:tr
            [:th.col-sm-8 label-task]
            [:th.col-sm-4 label-time]]]
          [:tbody
           (for [timeslip @timeslips]
             ^{:key (str "timeslip-row-" (:id timeslip))}
             [timeslip-row timeslip])]]]))))

(defn today []
  [:div.today
   [head-row]
   [timeslips-table]])

;; --- Projects view components

(defn project-show [{:keys [name client tasks] :as project}]
  [:tr {:id (str "project-" (:client project) "-" (:name project))}
   [:td client]
   [:td name]
   [:td (s/join ", " tasks)]
   [:td ]])

(defn project-row []
  (let [editing (r/atom false)]
    (fn [project]
      [project-show project])))

(defn projects-table []
  (let [projects (subscribe [:projects])]
    (fn []
      [:div.row
       [:table.table.table-hover
        [:thead
         [:tr
          [:th.col-sm-3 "Client"]
          [:th.col-sm-3 "Project"]
          [:th.col-sm-3 "Tasks"]
          [:th.col-sm-3 " "]]]
        [:tbody
         (for [{:keys [client name] :as project} @projects]
           ^{:key (str "project-row-" client "-" name)}
           [project-row project])]]])))

;; --- Panels ---

(defn timeslips-panel []
  [:div.timeslips-panel
   [running-timer]
   [today]])

(defn projects-panel []
  [:div.projects-panel
   [projects-table]])

;; --- Page ---

(defn page [env]
  (let [panel (subscribe [:panel])]
  [:div.container {:class "page"}
   [navigation @panel]
   (condp = @panel
     "timeslips" [timeslips-panel]
     "projects" [projects-panel])
   (when (= "dev" env)
     [debug-db])]))


