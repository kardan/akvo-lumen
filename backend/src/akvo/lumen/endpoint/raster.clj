(ns akvo.lumen.endpoint.raster
  (:require [akvo.lumen.component.tenant-manager :refer [connection]]
            [integrant.core :as ig]
            [akvo.lumen.lib.raster :as raster]
            [compojure.core :refer :all]))

(defn endpoint [{:keys [tenant-manager config]}]
  (context "/api/rasters" {:keys [params tenant] :as request}
    (let-routes [tenant-conn (connection tenant-manager tenant)]

      (GET "/" _
        (raster/all tenant-conn))

      (POST "/" {:keys [tenant body jwt-claims] :as request}
        (raster/create tenant-conn config jwt-claims body))

      (context "/:id" [id]
        (GET "/" _
          (raster/fetch tenant-conn id))

        (DELETE "/" _
          (raster/delete tenant-conn id))))))

(defmethod ig/init-key :akvo.lumen.endpoint.raster/raster  [_ opts]
  (endpoint (assoc opts :config (:config (:config opts)))))
